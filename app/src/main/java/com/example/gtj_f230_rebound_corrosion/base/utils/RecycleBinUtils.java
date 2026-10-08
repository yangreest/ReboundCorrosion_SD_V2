package com.example.gtj_f230_rebound_corrosion.base.utils;

import android.text.TextUtils;

import com.blankj.utilcode.util.SPUtils;
import com.orhanobut.logger.Logger;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

/**
 * 回收站工具类
 * <p>
 * “删除”只做软删除：把工程/构件的 id 登记到本地，列表中不再显示；
 * 登记的数据可在回收站中“恢复”（重新显示）或“彻底删除”（真正从数据库移除）。
 */
public class RecycleBinUtils {
    private static final String SP_KEY_RECYCLE_PROJECT = "recycle-bin-project-ids";
    private static final String SP_KEY_RECYCLE_ZONE = "recycle-bin-zone-ids";
    private static final String SEPARATOR = ",";

    private RecycleBinUtils() {
    }

    public static Set<Long> getRecycledProjectIds() {
        return readIds(SP_KEY_RECYCLE_PROJECT);
    }

    public static Set<Long> getRecycledZoneIds() {
        return readIds(SP_KEY_RECYCLE_ZONE);
    }

    public static boolean isProjectRecycled(Long projectId) {
        return projectId != null && readIds(SP_KEY_RECYCLE_PROJECT).contains(projectId);
    }

    public static boolean isZoneRecycled(Long zoneId) {
        return zoneId != null && readIds(SP_KEY_RECYCLE_ZONE).contains(zoneId);
    }

    public static void addProjects(Collection<Long> ids) {
        add(SP_KEY_RECYCLE_PROJECT, ids);
    }

    public static void addZones(Collection<Long> ids) {
        add(SP_KEY_RECYCLE_ZONE, ids);
    }

    public static void removeProjects(Collection<Long> ids) {
        remove(SP_KEY_RECYCLE_PROJECT, ids);
    }

    public static void removeZones(Collection<Long> ids) {
        remove(SP_KEY_RECYCLE_ZONE, ids);
    }

    public static void retainProjects(Collection<Long> ids) {
        retain(SP_KEY_RECYCLE_PROJECT, ids);
    }

    public static void retainZones(Collection<Long> ids) {
        retain(SP_KEY_RECYCLE_ZONE, ids);
    }

    private static void add(String key, Collection<Long> ids) {
        Set<Long> target = toIdSet(ids);
        if (target.isEmpty()) {
            return;
        }
        Set<Long> current = readIds(key);
        if (current.addAll(target)) {
            writeIds(key, current);
        }
    }

    private static void remove(String key, Collection<Long> ids) {
        Set<Long> target = toIdSet(ids);
        if (target.isEmpty()) {
            return;
        }
        Set<Long> current = readIds(key);
        if (current.removeAll(target)) {
            writeIds(key, current);
        }
    }

    private static void retain(String key, Collection<Long> ids) {
        Set<Long> current = readIds(key);
        if (current.retainAll(toIdSet(ids))) {
            writeIds(key, current);
        }
    }

    private static Set<Long> toIdSet(Collection<Long> ids) {
        Set<Long> idSet = new TreeSet<>();
        if (ids != null) {
            for (Long id : ids) {
                if (id != null) {
                    idSet.add(id);
                }
            }
        }
        return idSet;
    }

    private static Set<Long> readIds(String key) {
        Set<Long> ids = new TreeSet<>();
        String value;
        try {
            value = SPUtils.getInstance().getString(key, "");
        } catch (Exception e) {
            Logger.e("读取回收站记录失败", e);
            return ids;
        }
        if (TextUtils.isEmpty(value)) {
            return ids;
        }
        for (String item : value.split(SEPARATOR)) {
            if (TextUtils.isEmpty(item)) {
                continue;
            }
            try {
                ids.add(Long.parseLong(item.trim()));
            } catch (NumberFormatException e) {
                Logger.e("回收站记录格式异常：" + item);
            }
        }
        return ids;
    }

    private static void writeIds(String key, Set<Long> ids) {
        StringBuilder sbd = new StringBuilder();
        if (ids != null) {
            for (Long id : ids) {
                if (id == null) {
                    continue;
                }
                if (sbd.length() > 0) {
                    sbd.append(SEPARATOR);
                }
                sbd.append(id);
            }
        }
        SPUtils.getInstance().put(key, sbd.toString());
    }
}
