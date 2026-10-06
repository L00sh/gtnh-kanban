package com.gtnhkanban.client;

import java.util.ArrayList;
import java.util.List;

import com.gtnhkanban.api.RequirementView;

final class MaterialLeaves {

    static final class Leaf {

        final RequirementView requirement;
        final String context;

        Leaf(RequirementView requirement, String context) {
            this.requirement = requirement;
            this.context = context;
        }
    }

    static List<Leaf> of(List<RequirementView> roots) {
        List<Leaf> result = new ArrayList<Leaf>();
        for (RequirementView row : roots) append(row, "", result);
        return result;
    }

    private static void append(RequirementView row, String context, List<Leaf> leaves) {
        if (row.getChildren()
            .isEmpty() || row.isComplete()) leaves.add(new Leaf(row, context));
        else for (RequirementView child : row.getChildren()) append(child, MaterialDisplay.name(row.getItem()), leaves);
    }
}
