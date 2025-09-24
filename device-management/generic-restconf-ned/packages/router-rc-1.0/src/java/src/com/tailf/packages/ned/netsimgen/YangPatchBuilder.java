package com.tailf.packages.ned.netsimgen;

import com.tailf.ned.NedEditOp;
import com.tailf.ned.NedException;
import com.tailf.conf.*;
import java.util.*;
import java.util.function.Function;

final class YangPatchBuilder {

    interface Resolver extends java.util.function.Function<NedEditOp, PathInfo> {}

    static String fromOps(NedEditOp[] ops, Resolver resolver) throws NedException {
        String patchId = "nso-" + System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"yang-patch\":{");
        sb.append("\"patch-id\":\"").append(patchId).append("\",");
        sb.append("\"edit\":[");
        boolean first = true;

        for (NedEditOp op : ops) {
            if (op.getOpDone()) continue;

            PathInfo pi = resolver.apply(op);
            String target = "/" + pi.target;  // RFC8072: absolute to datastore root
            String operation;
            String valueJson = null;

            // Extra fields used by MOVE
            String moveWhere = null;  // "first" | "after"
            String movePoint = null;  // absolute target path of the anchor (for after)

            switch (op.getOperation()) {
                case NedEditOp.CREATED:
                    operation = "create";
                    valueJson = buildCreateValue(pi, op);
                    break;

                case NedEditOp.VALUE_SET:
                case NedEditOp.DEFAULT_SET:
                    operation = "merge";
                    valueJson = buildMergeValue(pi, op);
                    break;

                case NedEditOp.DELETED:
                    operation = "delete";
                    break;

                case NedEditOp.MOVED:
                    operation = "move";
                    switch (op.getMoveDestination()) {
                        case NedEditOp.FIRST:
                            moveWhere = "first";
                            break;
                        case NedEditOp.AFTER:
                            moveWhere = "after";
                            movePoint = buildMovePointPath(pi, target, op.getValue());
                            break;
                        default:
                            // Fall back to "after" if something unexpected; better than failing outright
                            moveWhere = "after";
                            movePoint = buildMovePointPath(pi, target, op.getValue());
                            break;
                    }
                    break;

                default:
                    operation = "merge";
            }

            if (!first) sb.append(',');
            first = false;
            sb.append('{');
            sb.append("\"edit-id\":\"").append(java.util.UUID.randomUUID()).append("\",");
            sb.append("\"operation\":\"").append(operation).append("\",");
            sb.append("\"target\":\"").append(escape(target)).append("\"");

            if ("move".equals(operation)) {
                sb.append(",\"where\":\"").append(moveWhere).append("\"");
                if (movePoint != null) {
                    sb.append(",\"point\":\"").append(escape(movePoint)).append("\"");
                }
            } else if (valueJson != null) {
                sb.append(",\"value\":").append(valueJson);
            }
            sb.append('}');
        }
        sb.append("]}}");
        return sb.toString();
    }

    // ---------- module-qualifying helpers ----------
    private static String moduleNameOf(com.tailf.maapi.MaapiSchemas.CSNode cs) {
        return cs.getSchema().getModule();  // actual YANG module-name (no revision)
    }
    private static String qname(PathInfo pi) {
        return moduleNameOf(pi.cs) + ":" + pi.nodeName;
    }

    private static String buildCreateValue(PathInfo pi, NedEditOp op) {
        final String qn = qname(pi);

        if (pi.cs.isList()) {
            StringBuilder o = new StringBuilder();
            o.append("{\"").append(qn).append("\":[{");
            for (int i = 0; i < pi.keyNames.size(); i++) {
                if (i > 0) o.append(',');
                o.append("\"").append(escape(pi.keyNames.get(i))).append("\":")
                 .append(jsonString(pi.keyValues.get(i)));
            }
            o.append("}]}");
            return o.toString();
        }
        if (pi.cs.isContainer()) {
            return "{\"" + escape(qn) + "\":{}}";
        }
        if (pi.cs.isLeaf()) {
            return "{\"" + escape(qn) + "\":" + valueFromConf(op.getValue()) + "}";
        }
        if (pi.cs.isLeafList()) {
            ConfObject v = op.getValue();
            if (v instanceof ConfList) {
                ConfList l = (ConfList) v;
                List<String> arr = new ArrayList<>();
                for (ConfObject e : l.elements()) {
                    arr.add(jsonString(((ConfValue)e).toString()));
                }
                return "{\"" + escape(qn) + "\":[" + String.join(",", arr) + "]}";
            }
            return "{\"" + escape(qn) + "\":[]}";
        }
        return "{}";
    }

    private static String buildMergeValue(PathInfo pi, NedEditOp op) {
        final String qn = qname(pi);

        if (pi.cs.isLeaf()) {
            return "{\"" + escape(qn) + "\":" + valueFromConf(op.getValue()) + "}";
        } else if (pi.cs.isLeafList()) {
            ConfList l = (ConfList) op.getValue();
            List<String> arr = new ArrayList<>();
            for (ConfObject e : l.elements()) {
                arr.add(jsonString(((ConfValue)e).toString()));
            }
            return "{\"" + escape(qn) + "\":[" + String.join(",", arr) + "]}";
        } else if (pi.cs.isContainer()) {
            return "{\"" + escape(qn) + "\":{}}";
        } else if (pi.cs.isList()) {
            // Merge on list node itself: send entry object with keys
            StringBuilder o = new StringBuilder();
            o.append("{\"").append(qn).append("\":[{");
            for (int i = 0; i < pi.keyNames.size(); i++) {
                if (i > 0) o.append(',');
                o.append("\"").append(escape(pi.keyNames.get(i))).append("\":")
                 .append(jsonString(pi.keyValues.get(i)));
            }
            o.append("}]}");
            return o.toString();
        }
        return null;
    }

    // ---------- MOVE helpers ----------
    private static String buildMovePointPath(PathInfo pi, String target, ConfObject value) {
        try {
            // Parent path = everything before the last segment ("/.../<list>=<keys>")
            int cut = target.lastIndexOf('/');
            String parent = (cut >= 0) ? target.substring(0, cut) : "";

            if (pi.cs.isList() && value instanceof ConfKey) {
                String csv = keyCsvFromConfKey((ConfKey) value);
                return parent + "/" + pi.nodeName + "=" + csv;
            } else if (pi.cs.isLeafList() && value instanceof ConfValue) {
                String enc = urlEncodeVal(((ConfValue) value).toString());
                return parent + "/" + pi.nodeName + "=" + enc;
            } else {
                // Unknown
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String keyCsvFromConfKey(ConfKey ck) throws java.io.UnsupportedEncodingException {
        // ConfKey.toString() is typically "{k1 k2 ...}"
        String s = ck.toString();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}"))   s = s.substring(0, s.length() - 1);
        s = s.trim();
        if (s.isEmpty()) return "";
        String[] parts = s.split("\\s+");
        StringBuilder csv = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) csv.append(',');
            csv.append(urlEncodeVal(parts[i]));
        }
        return csv.toString();
    }

    private static String urlEncodeVal(String s) throws java.io.UnsupportedEncodingException {
        String enc = java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8.name());
        // Keep commas as literal separators (we encode values, then join with literal commas)
        return enc.replace("+", "%20");
    }

    // ---------- generic helpers ----------
    private static String valueFromConf(ConfObject o) {
        if (o == null) return "null";
        if (o instanceof ConfBool) {
            return ((ConfBool)o).booleanValue() ? "true" : "false";
        } else if (o instanceof ConfUInt32 || o instanceof ConfEnumeration) {
            return o.toString();
        } else {
            return jsonString(o.toString());
        }
    }

    private static String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': b.append("\\\\"); break;
                case '"':  b.append("\\\""); break;
                case '\b': b.append("\\b");  break;
                case '\f': b.append("\\f");  break;
                case '\n': b.append("\\n");  break;
                case '\r': b.append("\\r");  break;
                case '\t': b.append("\\t");  break;
                default:   b.append(c);
            }
        }
        b.append('"');
        return b.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
