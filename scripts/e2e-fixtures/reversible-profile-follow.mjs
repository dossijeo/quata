export async function prepareReversibleProfileFollow({
  exists,
  insert,
  pollPresent,
  restore,
  retainSnapshot,
}) {
  const initiallyFollowing = await exists();
  const snapshot = { initiallyFollowing };
  retainSnapshot(snapshot);
  try {
    if (!initiallyFollowing) await insert();
    await pollPresent();
    return snapshot;
  } catch (error) {
    await restore(initiallyFollowing).catch(() => {});
    throw error;
  }
}
