package fcw.info;

import java.nio.file.Path;

public interface InfoHolder {
    void write(Path out);

    boolean isEmpty();
}
