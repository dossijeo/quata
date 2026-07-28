#include "ShareQueueLock.h"

#include <sys/file.h>

int32_t quata_flock(int32_t descriptor, int32_t operation) {
    return flock(descriptor, operation);
}
