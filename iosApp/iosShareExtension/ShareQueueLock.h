#ifndef QUATA_SHARE_QUEUE_LOCK_H
#define QUATA_SHARE_QUEUE_LOCK_H

#include <stdint.h>

/// Namespaced wrapper avoids Swift/Xcode 26 resolving `flock` as Darwin's struct flock.
int32_t quata_flock(int32_t descriptor, int32_t operation);

#endif
