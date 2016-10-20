package com.advancedtelematic.libostree;

import com.sun.jna.*;

public class LibOstree {
    interface AtsOstree extends Library {
        AtsOstree INSTANCE = (AtsOstree) Native.loadLibrary("atsostree", AtsOstree.class);

        String parentOf(String repoPath, String commit, Memory error);
    }

    public String parentOf(String repoPath, String commit) {
        Memory m = new Memory(1048576);
        m.clear();

        String res = AtsOstree.INSTANCE.parentOf(repoPath, commit, m);

        if((res == null) && (!m.getString(0).isEmpty())) {
            throw new LibOstreeException(m.getString(0));
        }

        return res;
    }
}
