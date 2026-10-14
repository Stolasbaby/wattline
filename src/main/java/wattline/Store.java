package wattline;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class Store {

    private Store() {}

    static File readable(File file) {
        if (file.isFile() && file.length() > 0) return file;
        File bak = new File(file.getParentFile(), file.getName() + ".bak");
        if (bak.isFile() && bak.length() > 0) return bak;
        return file;
    }

    static void yaml(File file, YamlConfiguration yaml) throws IOException {
        File dir = file.getParentFile();
        if (dir != null) dir.mkdirs();
        Path target = file.toPath();
        Path temp = target.resolveSibling(file.getName() + ".tmp");
        Path bak = target.resolveSibling(file.getName() + ".bak");
        yaml.save(temp.toFile());
        if (Files.exists(target)) Files.move(target, bak, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.deleteIfExists(bak);
    }
}
