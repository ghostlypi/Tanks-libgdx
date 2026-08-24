package theopalgames.tanks.android;

import android.content.ContentResolver;
import android.util.Log;
import androidx.documentfile.provider.DocumentFile;
import basewindow.BaseFile;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplication;

import java.io.*;
import java.util.ArrayList;

public class AndroidSAFFile extends BaseFile {
    public static DocumentFile root;
    public static AndroidApplication application;
    private DocumentFile documentFile;

    private String[] contents;
    private int readingLine;

    private StringBuilder writer;

    public AndroidSAFFile(String path) {
        super(path);
        this.documentFile = getDocumentFile(normalizePath(path), false);
    }

    private String normalizePath(String path) {
        if (path.startsWith("/.tanks/"))
            return path.substring("/.tanks/".length());
        else if (path.equals("/.tanks"))
            return "";
        return path;
    }

    private DocumentFile getDocumentFile(String path, boolean createParents) {
        if (root == null) return null;
        if (path.isEmpty()) return root;

        String[] parts = path.split("/");
        DocumentFile current = root;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            DocumentFile next = current.findFile(part);
            if (next == null) {
                if (createParents) {
                    current = current.createDirectory(part);
                    if (current == null) return null;
                } else {
                    return null;
                }
            } else {
                current = next;
            }
        }
        return current;
    }

    @Override
    public boolean exists() {
        return documentFile != null && documentFile.exists();
    }

    @Override
    public boolean create() throws IOException {
        if (exists()) return true;

        String normPath = normalizePath(path);
        String name = normPath;
        if (name.contains("/"))
            name = name.substring(name.lastIndexOf("/") + 1);

        String parentPath = normPath;
        if (parentPath.contains("/"))
            parentPath = parentPath.substring(0, parentPath.lastIndexOf("/"));
        else
            parentPath = "";

        DocumentFile parent = getDocumentFile(parentPath, true);
        if (parent == null) return false;

        documentFile = parent.createFile("application/octet-stream", name);
        return documentFile != null;
    }

    @Override
    public void renameTo(String name) {
        if (documentFile != null)
            documentFile.renameTo(name);
    }

    @Override
    public void delete() {
        if (documentFile != null)
            documentFile.delete();
    }

    @Override
    public ArrayList<String> getSubfiles() throws IOException {
        ArrayList<String> subfiles = new ArrayList<>();
        if (documentFile != null && documentFile.isDirectory()) {
            for (DocumentFile file : documentFile.listFiles()) {
                String p = this.path;
                if (!p.endsWith("/")) p += "/";
                subfiles.add(p + file.getName());
            }
        }
        return subfiles;
    }

    @Override
    public void startReading() throws FileNotFoundException {
        if (documentFile == null) documentFile = getDocumentFile(normalizePath(path), false);
        if (documentFile == null) throw new FileNotFoundException(path);
        try {
            ContentResolver resolver;
            if (Gdx.app instanceof AndroidApplication)
                resolver = ((AndroidApplication) Gdx.app).getContentResolver();
            else
                resolver = application.getContentResolver();
            InputStream in = resolver.openInputStream(documentFile.getUri());
            if (in == null) throw new FileNotFoundException(path);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            in.close();
            contents = out.toString().replace("\r\n", "\n").split("\n");
            readingLine = 0;
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public boolean hasNextLine() {
        return contents != null && readingLine < contents.length;
    }

    @Override
    public String nextLine() {
        String s = contents[readingLine];
        readingLine++;
        return s;
    }

    @Override
    public void stopReading() {
        contents = null;
        readingLine = 0;
    }

    @Override
    public void startWriting() {
        this.writer = new StringBuilder();
    }

    @Override
    public void println(String s) {
        if (this.writer != null) {
            this.writer.append(s).append("\n");
        }
    }

    @Override
    public void stopWriting() {
        if (this.writer == null) return;
        try {
            if (documentFile == null) create();
            if (documentFile == null) {
                Log.e("Tanks", "Failed to create file for writing: " + path);
                return;
            }

            ContentResolver resolver;
            if (Gdx.app instanceof AndroidApplication)
                resolver = ((AndroidApplication) Gdx.app).getContentResolver();
            else
                resolver = application.getContentResolver();
            OutputStream out = resolver.openOutputStream(documentFile.getUri(), "wt");
            if (out != null) {
                out.write(this.writer.toString().getBytes());
                out.close();
            }
        } catch (IOException e) {
            Log.e("Tanks", "Failed to write file: " + path, e);
        }
        this.writer = null;
    }

    @Override
    public void mkdirs() {
        documentFile = getDocumentFile(normalizePath(path), true);
    }

    @Override
    public long lastModified() {
        return documentFile != null ? documentFile.lastModified() : 0;
    }
}
