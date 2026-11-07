package com.github.tvbox.osc.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * P-12：FileUtils.recursiveDelete 边界回归（性能审查报告点名的工具类测试之一）。
 */
public class FileUtilsRecursiveDeleteTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void nonExistentPathIsNoOp() {
        File ghost = new File(tmp.getRoot(), "ghost");
        FileUtils.recursiveDelete(ghost); // 不得抛异常
        assertFalse(ghost.exists());
    }

    @Test
    public void deletesSingleFile() throws IOException {
        File f = tmp.newFile("a.txt");
        FileUtils.recursiveDelete(f);
        assertFalse(f.exists());
    }

    @Test
    public void deletesEmptyDirectory() throws IOException {
        File dir = tmp.newFolder("empty");
        FileUtils.recursiveDelete(dir);
        assertFalse(dir.exists());
    }

    @Test
    public void deletesNestedTree() throws IOException {
        File root = tmp.newFolder("tree");
        File deep = new File(root, "l1/l2/l3");
        assertTrue(deep.mkdirs() || deep.isDirectory());
        for (int i = 0; i < 3; i++) {
            assertTrue(new File(deep, "f" + i + ".bin").createNewFile());
        }
        assertTrue(new File(root, "l1/top.txt").createNewFile());
        byte[] payload = new byte[1024];
        FileOutputStream out = new FileOutputStream(new File(deep, "big.bin"));
        out.write(payload);
        out.close();

        FileUtils.recursiveDelete(root);
        assertFalse("嵌套树必须整体删除", root.exists());
    }
}
