package ref.android.os;

import java.io.FileDescriptor;

import menu.nextgen.reflect.annotation.BClassName;
import menu.nextgen.reflect.annotation.BMethod;

@BClassName("android.os.MemoryFile")
public interface MemoryFile {
    @BMethod
    FileDescriptor getFileDescriptor();
}
