package com.quata.core.platform

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Browser microphone recorder backed by `getUserMedia` and `MediaRecorder`.
 * Browser objects remain in a small JS registry keyed by id so the Kotlin/Wasm boundary only
 * carries primitive values and the resulting Blob URL.
 */
class BrowserAudioRecorderService : AudioRecorderService {
    private var recordingId: String? = null
    private var nextId = 0L

    override suspend fun start(options: AudioRecordingOptions): PlatformResult<Unit> {
        cancel()
        val id = "quata-recorder-${++nextId}"
        return suspendCoroutine { continuation ->
            browserRecorderStart(id, options.mimeType, options.maxDurationMillis) { result ->
                val mapped = result.toRecorderUnitResult()
                if (mapped is PlatformResult.Success) recordingId = id
                continuation.resume(mapped)
            }
        }
    }

    override suspend fun stop(): PlatformResult<AudioRecording> {
        val id = recordingId ?: return PlatformResult.Failure("recorder_not_started")
        recordingId = null
        return suspendCoroutine { continuation ->
            browserRecorderStop(id) { result -> continuation.resume(result.toRecordingResult()) }
        }
    }

    override suspend fun cancel(): PlatformResult<Unit> {
        val id = recordingId ?: return PlatformResult.Success(Unit)
        recordingId = null
        return suspendCoroutine { continuation ->
            browserRecorderCancel(id) { result -> continuation.resume(result.toRecorderUnitResult()) }
        }
    }
}

private fun String.toRecorderUnitResult(): PlatformResult<Unit> = when {
    this == BrowserRecorderUnsupported -> PlatformResult.Unsupported
    startsWith(BrowserRecorderFailurePrefix) -> PlatformResult.Failure(removePrefix(BrowserRecorderFailurePrefix))
    this == BrowserRecorderSuccess -> PlatformResult.Success(Unit)
    else -> PlatformResult.Failure("web_audio_recorder_invalid_result")
}

private fun String.toRecordingResult(): PlatformResult<AudioRecording> = when {
    this == BrowserRecorderUnsupported -> PlatformResult.Unsupported
    startsWith(BrowserRecorderFailurePrefix) -> PlatformResult.Failure(removePrefix(BrowserRecorderFailurePrefix))
    else -> runCatching {
        val value = Json.parseToJsonElement(this).jsonObject
        val reference = value["reference"]?.jsonPrimitive?.contentOrNull
            ?: error("web_audio_recorder_missing_reference")
        val mimeType = value["mimeType"]?.jsonPrimitive?.contentOrNull ?: "audio/webm"
        PlatformResult.Success(
            AudioRecording(
                file = PlatformFile(
                    reference = reference,
                    displayName = value["displayName"]?.jsonPrimitive?.contentOrNull,
                    mimeType = mimeType,
                    sizeBytes = value["sizeBytes"]?.jsonPrimitive?.longOrNull,
                ),
                durationMillis = value["durationMillis"]?.jsonPrimitive?.longOrNull ?: 0L,
                mimeType = mimeType,
            ),
        )
    }.getOrElse { PlatformResult.Failure("web_audio_recorder_invalid_result") }
}

private fun browserRecorderStart(
    id: String,
    requestedMimeType: String,
    maxDurationMillis: Long?,
    onResult: (String) -> Unit,
): Unit = js(
    """
    (() => {
      try {
        const media = globalThis.navigator?.mediaDevices;
        if (!media || typeof media.getUserMedia !== 'function' || typeof globalThis.MediaRecorder !== 'function') {
          onResult('unsupported');
          return;
        }
        media.getUserMedia({ audio: true }).then((stream) => {
          const chooseMime = () => {
            const preferred = requestedMimeType || '';
            if (preferred && (!MediaRecorder.isTypeSupported || MediaRecorder.isTypeSupported(preferred))) return preferred;
            for (const candidate of ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus']) {
              if (!MediaRecorder.isTypeSupported || MediaRecorder.isTypeSupported(candidate)) return candidate;
            }
            return '';
          };
          let recorder;
          try {
            const mimeType = chooseMime();
            recorder = mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream);
          } catch (error) {
            stream.getTracks().forEach((track) => track.stop());
            onResult('failure:' + (error?.name || 'web_audio_recorder_create_failed'));
            return;
          }
          const store = globalThis.__quataAudioRecorders || (globalThis.__quataAudioRecorders = new Map());
          const record = { recorder, stream, chunks: [], startedAt: Date.now(), maxTimer: null, discard: false };
          let startReported = false;
          store.set(id, record);
          recorder.ondataavailable = (event) => { if (event.data?.size > 0) record.chunks.push(event.data); };
          recorder.onerror = (event) => {
            store.delete(id);
            stream.getTracks().forEach((track) => track.stop());
            if (!startReported) onResult('failure:' + (event?.error?.name || 'web_audio_recorder_failed'));
          };
          try {
            recorder.start();
            const max = Number(maxDurationMillis || 0);
            if (Number.isFinite(max) && max > 0) {
              record.maxTimer = globalThis.setTimeout(() => { if (recorder.state === 'recording') recorder.stop(); }, max);
            }
            startReported = true;
            onResult('success');
          } catch (error) {
            store.delete(id);
            stream.getTracks().forEach((track) => track.stop());
            onResult('failure:' + (error?.name || 'web_audio_recorder_start_failed'));
          }
        }).catch((error) => onResult('failure:' + (error?.name || 'microphone_permission_denied')));
      } catch (_) { onResult('unsupported'); }
    })();
    """,
)

private fun browserRecorderStop(id: String, onResult: (String) -> Unit): Unit = js(
    """(() => {
      const store = globalThis.__quataAudioRecorders;
      const record = store?.get(id);
      if (!record) return onResult('failure:recorder_not_started');
      const finish = () => {
        store.delete(id);
        if (record.maxTimer) globalThis.clearTimeout(record.maxTimer);
        record.stream.getTracks().forEach((track) => track.stop());
        if (record.discard) return onResult('success');
        const mimeType = record.recorder.mimeType || record.chunks[0]?.type || 'audio/webm';
        const blob = new Blob(record.chunks, { type: mimeType });
        if (!blob.size) return onResult('failure:recording_empty');
        const extension = mimeType.includes('ogg') ? 'ogg' : mimeType.includes('mp4') ? 'm4a' : 'webm';
        onResult(JSON.stringify({ reference: globalThis.URL.createObjectURL(blob), displayName: `quata_audio_${'$'}{Date.now()}.${'$'}{extension}`, mimeType, sizeBytes: blob.size, durationMillis: Math.max(0, Date.now() - record.startedAt) }));
      };
      record.recorder.onstop = finish;
      if (record.recorder.state === 'inactive') finish(); else record.recorder.stop();
    })();""",
)

private fun browserRecorderCancel(id: String, onResult: (String) -> Unit): Unit = js(
    """(() => {
      const store = globalThis.__quataAudioRecorders;
      const record = store?.get(id);
      if (!record) return onResult('success');
      record.discard = true;
      record.recorder.onstop = () => {
        store.delete(id);
        if (record.maxTimer) globalThis.clearTimeout(record.maxTimer);
        record.stream.getTracks().forEach((track) => track.stop());
        onResult('success');
      };
      if (record.recorder.state === 'inactive') record.recorder.onstop(); else record.recorder.stop();
    })();""",
)

private const val BrowserRecorderUnsupported = "unsupported"
private const val BrowserRecorderSuccess = "success"
private const val BrowserRecorderFailurePrefix = "failure:"
