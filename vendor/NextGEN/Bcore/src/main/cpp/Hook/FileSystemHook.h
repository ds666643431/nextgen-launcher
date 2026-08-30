#ifndef VIRTUALM_FILESYSTEMHOOK_H
#define VIRTUALM_FILESYSTEMHOOK_H

class FileSystemHook {
public:
    static void init();
    static void setFakeUid(int uid);
};

#endif
