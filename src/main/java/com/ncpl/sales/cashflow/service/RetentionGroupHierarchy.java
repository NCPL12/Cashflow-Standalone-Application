package com.ncpl.sales.cashflow.service;

import java.util.*;

/** Resolves authoritative Tally group membership without guessing from ledger names. */
public final class RetentionGroupHierarchy {
    private RetentionGroupHierarchy() {}
    private static String key(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    public static boolean isRetentionLedger(String ledgerParent, String retentionRoot, Map<String,String> groupParents) {
        Map<String,String> normalized = new HashMap<>();
        groupParents.forEach((name,parent) -> normalized.put(key(name), key(parent)));
        String root = key(retentionRoot);
        if (root.isEmpty() || !under(root, "sundry debtors", normalized) || !under("sundry debtors", "current assets", normalized)) return false;
        return under(key(ledgerParent),root,normalized);
    }
    private static boolean under(String group,String root,Map<String,String> parents) {
        Set<String> seen=new HashSet<>();
        while (!group.isEmpty() && seen.add(group)) {
            if (group.equals(root)) return true;
            group=parents.getOrDefault(group, "");
        }
        return false;
    }
}
