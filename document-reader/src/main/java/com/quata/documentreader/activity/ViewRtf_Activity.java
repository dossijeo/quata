package com.quata.documentreader.activity;


import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintJob;
import android.print.PrintManager;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import com.quata.documentreader.DocumentReaderChrome;
import com.quata.documentreader.R;
import com.quata.documentreader.QuataDocumentReaderTheme;
import com.quata.documentreader.databinding.ActivityViewRtfBinding;
import com.quata.documentreader.util.RtfHtmlDataType;
import com.quata.documentreader.util.RtfParseException;
import com.quata.documentreader.util.RtfReader;
import com.quata.documentreader.util.Utility;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class ViewRtf_Activity extends AppCompatActivity {

    private static final String TAG = "ViewRtfActivity";

    ActivityViewRtfBinding binding;
    private String fileName;
    private String filePath;
    Boolean fromConverterApp = false;
    boolean isExit = false;
    boolean isFromAppActivity = false;
    PrintDocumentAdapter printAdapter;
    PrintJob printJob;
    WebView webview;
    private boolean back = false;
    private LoadTextFromRtfFile loadTask;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        QuataDocumentReaderTheme.apply(this);
        super.onCreate(savedInstanceState);

        binding = ActivityViewRtfBinding.inflate(getLayoutInflater());
        View view = binding.getRoot();
        setContentView(view);

        if (getIntent() != null) {
            this.filePath = getIntent().getStringExtra("path");
            this.fileName = getIntent().getStringExtra("name");
            this.isFromAppActivity = getIntent().getBooleanExtra("fromAppActivity", false);
            this.fromConverterApp = getIntent().getBooleanExtra("fromConverterApp", false);
            binding.headerTitleText.setMaxLines(1);

        }
        DocumentReaderChrome.configureHeader(
                this,
                binding.activityRoot,
                binding.headerTitleText,
                binding.imgBack,
                binding.imgPrint,
                binding.imgShare,
                this.fileName,
                this.filePath
        );
        binding.imgPrint.setOnClickListener(v -> printAndShare());
        WebView webView = (WebView) findViewById(R.id.webView);
        this.webview = webView;
        webView.setWebViewClient(new WebViewClient());
        this.webview.getSettings().setBuiltInZoomControls(true);
        this.webview.getSettings().setDisplayZoomControls(false);
        this.webview.getSettings().setAllowFileAccess(true);
        loadTask = new LoadTextFromRtfFile();
        loadTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
       // Utility.Toast(this, "Please wait...");
    }



    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void createWebPrintJob(WebView webView) {
        PrintManager printManager = (PrintManager) getSystemService(PRINT_SERVICE);
        this.printAdapter = webView.createPrintDocumentAdapter("New_RTF_File.pdf");
        this.printJob = printManager.print(getString(R.string.app_name)
                + " " + getString(R.string.quata_document_reader_print_job), this.printAdapter, new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).setResolution(new PrintAttributes
                        .Resolution("id", "print", 200, 200))
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR).setMinMargins(PrintAttributes
                        .Margins.NO_MARGINS).build());
    }


    class LoadTextFromRtfFile extends AsyncTask<Void, Void, File> {
        private boolean usedCache = false;
        private long parseMs = 0L;
        private long writeMs = 0L;
        private String errorMessage = null;

        LoadTextFromRtfFile() {
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            back = false;
            binding.progressBar.setVisibility(View.VISIBLE);
        }


        @RequiresApi(api = Build.VERSION_CODES.N)
        protected File doInBackground(Void... voidArr) {
            File file = new File(ViewRtf_Activity.this.filePath);
            if (!file.exists()) {
                errorMessage = "RTF source file does not exist";
                return null;
            }

            File htmlFile = getCachedHtmlFile(file);
            if (htmlFile.exists() && htmlFile.length() > 0L) {
                usedCache = true;
                return htmlFile;
            }

            RtfReader rtfReader = new RtfReader();
            RtfHtmlDataType rtfHtmlDataType = new RtfHtmlDataType();
            try {
                long parseStart = System.currentTimeMillis();
                rtfReader.parse(file);
                String html = rtfHtmlDataType.format(rtfReader.root, true);
                parseMs = System.currentTimeMillis() - parseStart;
                if (isCancelled()) {
                    return null;
                }

                long writeStart = System.currentTimeMillis();
                writeHtmlFile(htmlFile, html);
                writeMs = System.currentTimeMillis() - writeStart;
                return htmlFile;
            } catch (RtfParseException e) {
                errorMessage = e.getMessage();
                Utility.logCatMsg("RtfParseException " + e.getMessage());
                e.printStackTrace();
                return null;
            } catch (IOException e) {
                errorMessage = e.getMessage();
                Log.w(TAG, "Unable to cache rendered RTF", e);
                return null;
            }
        }

        @Override
        protected void onPostExecute(File htmlFile) {
            super.onPostExecute(htmlFile);
            loadTask = null;
            back = true;
            if (isFinishing() || ViewRtf_Activity.this.webview == null) {
                return;
            }
            if (htmlFile != null) {
                Log.d(TAG, "RTF ready cache=" + usedCache + " parseMs=" + parseMs + " writeMs=" + writeMs + " bytes=" + htmlFile.length());
                ViewRtf_Activity.this.webview.loadUrl(Uri.fromFile(htmlFile).toString());
            } else {
                binding.progressBar.setVisibility(View.GONE);
                Log.w(TAG, "RTF render failed: " + errorMessage);
                ViewRtf_Activity.this.webview.loadDataWithBaseURL("", "", "text/html", "UTF-8", "");
            }
        }

        @Override
        protected void onCancelled(File htmlFile) {
            super.onCancelled(htmlFile);
            loadTask = null;
            back = true;
        }
    }

    private File getCachedHtmlFile(File sourceFile) {
        File cacheDir = new File(getCacheDir(), "document_reader_rtf");
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            return new File(getCacheDir(), buildRtfCacheName(sourceFile));
        }
        return new File(cacheDir, buildRtfCacheName(sourceFile));
    }

    private String buildRtfCacheName(File sourceFile) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (FileInputStream inputStream = new FileInputStream(sourceFile)) {
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return toHex(digest.digest()) + ".html";
        } catch (Exception e) {
            String identity = sourceFile.getAbsolutePath() + ":" + sourceFile.length() + ":" + sourceFile.lastModified();
            return Math.abs(identity.hashCode()) + ".html";
        }
    }

    private String toHex(byte[] hash) {
        StringBuilder hex = new StringBuilder(hash.length * 2);
        char[] digits = "0123456789abcdef".toCharArray();
        for (byte value : hash) {
            int unsigned = value & 0xff;
            hex.append(digits[unsigned >>> 4]);
            hex.append(digits[unsigned & 0x0f]);
        }
        return hex.toString();
    }

    private void writeHtmlFile(File htmlFile, String html) throws IOException {
        File parent = htmlFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create RTF cache directory");
        }
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(htmlFile), StandardCharsets.UTF_8))) {
            writer.write(html);
        }
    }


    public class WebViewClient extends android.webkit.WebViewClient {
        public WebViewClient() {
        }

        @Override
        public void onPageStarted(WebView webView, String str, Bitmap bitmap) {
            super.onPageStarted(webView, str, bitmap);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView webView, String str) {
            webView.loadUrl(str);
            return true;
        }

        @Override
        public void onPageFinished(WebView webView, String str) {
            super.onPageFinished(webView, str);
            ViewRtf_Activity.this.findViewById(R.id.progressBar).setVisibility(View.GONE);
        }
    }

    private void shareFile() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        Uri parse = Uri.parse(this.filePath);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_STREAM, parse);
        startActivity(Intent.createChooser(intent, getString(R.string.shareUsing)));

    }


    private void printAndShare() {
        createWebPrintJob(this.webview);

    }


    @Override
    public void onBackPressed() {
        if(back){
            finish();
        }else {
            Toast.makeText(this, R.string.quata_document_reader_wait, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (loadTask != null) {
            loadTask.cancel(true);
            loadTask = null;
        }
        if (webview != null) {
            webview.stopLoading();
        }
        super.onDestroy();
    }

}
