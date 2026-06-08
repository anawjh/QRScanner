package com.qrscanner;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ProjectManager {
    private static final String DIR = "projects";
    private final File dir;

    public ProjectManager(Context context) {
        dir = new File(context.getFilesDir(), DIR);
        if (!dir.exists()) dir.mkdirs();
    }

    public List<String> listProjects() {
        List<String> list = new ArrayList<>();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files != null) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : files) {
                list.add(f.getName().replace(".json", ""));
            }
        }
        return list;
    }

    public ScanProject load(String name) {
        File file = new File(dir, sanitize(name) + ".json");
        if (!file.exists()) return null;
        try (FileReader fr = new FileReader(file)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = fr.read(buf)) != -1) sb.append(buf, 0, n);
            return ScanProject.fromJson(new JSONObject(sb.toString()));
        } catch (Exception e) {
            return null;
        }
    }

    public void save(ScanProject project) {
        project.touch();
        File file = new File(dir, sanitize(project.name) + ".json");
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(project.toJson().toString(2));
        } catch (Exception ignored) {}
    }

    public void delete(String name) {
        new File(dir, sanitize(name) + ".json").delete();
    }

    public ScanProject createNew() {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String name = "扫码记录_" + ts;
        int seq = 1;
        while (new File(dir, sanitize(name) + ".json").exists()) {
            name = "扫码记录_" + ts + "_" + (seq++);
        }
        ScanProject p = new ScanProject(name);
        save(p);
        return p;
    }

    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
