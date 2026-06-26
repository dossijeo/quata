package com.quata.documentreader.util;

import java.util.ArrayList;
import java.util.List;

public class RtfElementGroup extends RtfElement {
    public RtfElementGroup parent;
    public List<RtfElement> children;

    public RtfElementGroup() {
        parent = null;
        children = new ArrayList<>();
    }

    public String getType() {
        // No children?
        if (children.isEmpty()) {
            return "";
        }

        // First child not a control word?
        RtfElement child = children.get(0);
        if (!(child instanceof RtfWordControl)) {
            return "";
        }

        return ((RtfWordControl) child).word;
    }

    public boolean isDestination() {
        // No children?
        if (children.isEmpty()) {
            return false;
        }

        // First child not a control symbol?
        RtfElement child = children.get(0);
        if (!(child instanceof RtfElementSymbol)) {
            return false;
        }

        return ((RtfElementSymbol) child).symbol == '*';
    }

    public void dump() {
        dump(0);
    }

    @Override
    public void dump(int level) {
    }
}
