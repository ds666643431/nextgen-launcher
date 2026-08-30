



#ifndef NEXTGEN_VMCLASSLOADERHOOK_H
#define NEXTGEN_VMCLASSLOADERHOOK_H


#include "BaseHook.h"
#include <jni.h>

class VMClassLoaderHook : public BaseHook {
public:
    static void hideXposed();
    static void init(JNIEnv *env);
};


#endif 
