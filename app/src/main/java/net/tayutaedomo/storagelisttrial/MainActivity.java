package net.tayutaedomo.storagelisttrial;

import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView tv = (TextView) findViewById(R.id.text);
        //tv.setText("testtest");


        // Result is empty array.
        HashSet<String> mounts = getExternalMounts();
        Log.d("getExternalMounts", mounts.toString());


        // Result is
        //   "/storage/emulated/0, false, false, -1"
        //   "/mnt/media_rw/3965-6433, false, true, 1"
        List<StorageUtils.StorageInfo> storageList = StorageUtils.getStorageList();
        Log.d("getStorageList", storageList.toString());

        for (Iterator<StorageUtils.StorageInfo> ite = storageList.iterator(); ite.hasNext();) {
            StorageUtils.StorageInfo info = ite.next();
            Log.d("getStorageList", info.path + ", " + info.readonly + ", " + info.removable + ", " + info.number);
        }


        // Result is error. /system/etc/vold.fstab is not found.
        //HashSet<String> storageSet = getStorageSet();


        // Refer: https://stackoverflow.com/questions/32064199/how-to-get-path-to-storage-directory-in-android
        Log.d("System.getenv", System.getenv("EXTERNAL_STORAGE"));
        //Log.d("System.getenv", System.getenv("SECONDARY_STORAGE")); // Error occurred


        File storage = new File("/storage");
        Log.d("/storage", storage.toString());

        File[] childFiles = storage.listFiles();
        for (int i = 0; i < childFiles.length; i++) {
            Log.d("/storage", childFiles[i].toString());
        }
    }


    // Refer: https://stackoverflow.com/questions/11281010/how-can-i-get-external-sd-card-path-for-android-4-0
    public HashSet<String> getExternalMounts() {
        final HashSet<String> out = new HashSet<String>();
        String reg = "(?i).*vold.*(vfat|ntfs|exfat|fat32|ext3|ext4).*rw.*";
        String s = "";
        try {
            final Process process = new ProcessBuilder().command("mount")
                    .redirectErrorStream(true).start();
            process.waitFor();
            final InputStream is = process.getInputStream();
            final byte[] buffer = new byte[1024];
            while (is.read(buffer) != -1) {
                s = s + new String(buffer);
            }
            is.close();
        } catch (final Exception e) {
            e.printStackTrace();
        }

        // parse output
        final String[] lines = s.split("\n");
        for (String line : lines) {
            if (!line.toLowerCase(Locale.US).contains("asec")) {
                if (line.matches(reg)) {
                    String[] parts = line.split(" ");
                    for (String part : parts) {
                        if (part.startsWith("/"))
                            if (!part.toLowerCase(Locale.US).contains("vold"))
                                out.add(part);
                    }
                }
            }
        }

        return out;
    }


    // Refer: https://stackoverflow.com/questions/7450650/how-to-list-additional-external-storage-folders-mount-points
    public HashSet<String> getStorageSet(){
        HashSet<String> storageSet = getStorageSet(new File("/system/etc/vold.fstab"), true);
        storageSet.addAll(getStorageSet(new File("/proc/mounts"), false));

        if (storageSet == null || storageSet.isEmpty()) {
            storageSet = new HashSet<String>();
            storageSet.add(Environment.getExternalStorageDirectory().getAbsolutePath());
        }
        return storageSet;
    }

    public HashSet<String> getStorageSet(File file, boolean is_fstab_file) {
        HashSet<String> storageSet = new HashSet<String>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String line;
            while ((line = reader.readLine()) != null) {
                HashSet<String> _storage = null;
                if (is_fstab_file) {
                    _storage = parseVoldFile(line);
                } else {
                    _storage = parseMountsFile(line);
                }
                if (_storage == null)
                    continue;
                storageSet.addAll(_storage);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally{
            try {
                reader.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            reader = null;
        }
        /*
         * set default external storage
         */
        storageSet.add(Environment.getExternalStorageDirectory().getAbsolutePath());
        return storageSet;
    }

    private HashSet<String> parseMountsFile(String str) {
        if (str == null)
            return null;
        if (str.length()==0)
            return null;
        if (str.startsWith("#"))
            return null;
        HashSet<String> storageSet = new HashSet<String>();
        /*
         * /dev/block/vold/179:19 /mnt/sdcard2 vfat rw,dirsync,nosuid,nodev,noexec,relatime,uid=1000,gid=1015,fmask=0002,dmask=0002,allow_utime=0020,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro 0 0
         * /dev/block/vold/179:33 /mnt/sdcard vfat rw,dirsync,nosuid,nodev,noexec,relatime,uid=1000,gid=1015,fmask=0002,dmask=0002,allow_utime=0020,codepage=cp437,iocharset=iso8859-1,shortname=mixed,utf8,errors=remount-ro 0 0
         */
        Pattern patter = Pattern.compile("/dev/block/vold.*?(/mnt/.+?) vfat .*");
        Matcher matcher = patter.matcher(str);
        boolean b = matcher.find();
        if (b) {
            String _group = matcher.group(1);
            storageSet.add(_group);
        }

        return storageSet;
    }

    private HashSet<String> parseVoldFile(String str) {
        if (str == null)
            return null;
        if (str.length()==0)
            return null;
        if (str.startsWith("#"))
            return null;
        HashSet<String> storageSet = new HashSet<String>();
        /*
         * dev_mount sdcard /mnt/sdcard auto /devices/platform/msm_sdcc.1/mmc_host
         * dev_mount SdCard /mnt/sdcard/extStorages /mnt/sdcard/extStorages/SdCard auto sd /devices/platform/s3c-sdhci.2/mmc_host/mmc1
         */
        Pattern patter1 = Pattern.compile("(/mnt/[^ ]+?)((?=[ ]+auto[ ]+)|(?=[ ]+(\\d*[ ]+)))");
        /*
         * dev_mount ins /mnt/emmc emmc /devices/platform/msm_sdcc.3/mmc_host
         */
        Pattern patter2 = Pattern.compile("(/mnt/.+?)[ ]+");
        Matcher matcher1 = patter1.matcher(str);
        boolean b1 = matcher1.find();
        if (b1) {
            String _group = matcher1.group(1);
            storageSet.add(_group);
        }

        Matcher matcher2 = patter2.matcher(str);
        boolean b2 = matcher2.find();
        if (!b1 && b2) {
            String _group = matcher2.group(1);
            storageSet.add(_group);
        }
        /*
         * dev_mount ins /storage/emmc emmc /devices/sdi2/mmc_host/mmc0/mmc0:0001/block/mmcblk0/mmcblk0p
         */
        Pattern patter3 = Pattern.compile("/.+?(?= )");
        Matcher matcher3 = patter3.matcher(str);
        boolean b3 = matcher3.find();
        if (!b1 && !b2 && b3) {
            String _group = matcher3.group(1);
            storageSet.add(_group);
        }
        return storageSet;
    }
}


// Refer: https://stackoverflow.com/questions/9340332/how-can-i-get-the-list-of-mounted-external-storage-of-android-device/19982338#19982338
class StorageUtils {

    private static final String TAG = "StorageUtils";

    public static class StorageInfo {

        public final String path;
        public final boolean readonly;
        public final boolean removable;
        public final int number;

        StorageInfo(String path, boolean readonly, boolean removable, int number) {
            this.path = path;
            this.readonly = readonly;
            this.removable = removable;
            this.number = number;
        }

        public String getDisplayName() {
            StringBuilder res = new StringBuilder();
            if (!removable) {
                res.append("Internal SD card");
            } else if (number > 1) {
                res.append("SD card " + number);
            } else {
                res.append("SD card");
            }
            if (readonly) {
                res.append(" (Read only)");
            }
            return res.toString();
        }
    }

    public static List<StorageInfo> getStorageList() {

        List<StorageInfo> list = new ArrayList<StorageInfo>();
        String def_path = Environment.getExternalStorageDirectory().getPath();
        boolean def_path_removable = Environment.isExternalStorageRemovable();
        String def_path_state = Environment.getExternalStorageState();
        boolean def_path_available = def_path_state.equals(Environment.MEDIA_MOUNTED)
                || def_path_state.equals(Environment.MEDIA_MOUNTED_READ_ONLY);
        boolean def_path_readonly = Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED_READ_ONLY);

        HashSet<String> paths = new HashSet<String>();
        int cur_removable_number = 1;

        if (def_path_available) {
            paths.add(def_path);
            list.add(0, new StorageInfo(def_path, def_path_readonly, def_path_removable, def_path_removable ? cur_removable_number++ : -1));
        }

        BufferedReader buf_reader = null;
        try {
            buf_reader = new BufferedReader(new FileReader("/proc/mounts"));
            String line;
            Log.d(TAG, "/proc/mounts");
            while ((line = buf_reader.readLine()) != null) {
                Log.d(TAG, line);
                if (line.contains("vfat") || line.contains("/mnt")) {
                    StringTokenizer tokens = new StringTokenizer(line, " ");
                    String unused = tokens.nextToken(); //device
                    String mount_point = tokens.nextToken(); //mount point
                    if (paths.contains(mount_point)) {
                        continue;
                    }
                    unused = tokens.nextToken(); //file system
                    List<String> flags = Arrays.asList(tokens.nextToken().split(",")); //flags
                    boolean readonly = flags.contains("ro");

                    if (line.contains("/dev/block/vold")) {
                        if (!line.contains("/mnt/secure")
                                && !line.contains("/mnt/asec")
                                && !line.contains("/mnt/obb")
                                && !line.contains("/dev/mapper")
                                && !line.contains("tmpfs")) {
                            paths.add(mount_point);
                            list.add(new StorageInfo(mount_point, readonly, true, cur_removable_number++));
                        }
                    }
                }
            }

        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (buf_reader != null) {
                try {
                    buf_reader.close();
                } catch (IOException ex) {}
            }
        }

        return list;
    }
}

