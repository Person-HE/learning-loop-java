package com.closeloop.knowledge;

import com.closeloop.config.AppProperties;
import com.closeloop.state.model.Kp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 知识库服务（只读层）：扫描 02-技术学习资料/后端知识库 → 知识点注册表。
 * 与 Node 版 kb.js 行为对齐：域目录 → 章 assets 归属 → frontmatter 元信息 → 锚点 → id 去重。
 */
@Component
public class KbService {

    private static final Logger log = LoggerFactory.getLogger(KbService.class);
    private static final Set<String> ASSET_EXT = Set.of(".svg", ".html", ".png", ".jpg", ".jpeg", ".gif", ".webp");

    private final AppProperties props;
    private volatile KnowledgeBase cache;

    public KbService(AppProperties props) {
        this.props = props;
    }

    public KnowledgeBase getKb(boolean refresh) {
        KnowledgeBase kb = cache;
        if (kb != null && !refresh) return kb;
        synchronized (this) {
            if (cache != null && !refresh) return cache;
            cache = scan();
            return cache;
        }
    }

    /** 读取知识点文档全文；缺失返回 null */
    public String readDoc(Kp kp) {
        if (kp == null || kp.path == null || kp.path.isEmpty()) return null;
        try {
            return Files.readString(props.kbRootPath().resolve(kp.path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** 按学习路径排序（域路径序 → 章自然序 → 标题） */
    public List<Kp> orderKpsByPath(List<Kp> kps, String category) {
        Map<String, Integer> pathOrder = new LinkedHashMap<>();
        List<Map<String, String>> pp = KbScanner.PLANNED_PATHS.getOrDefault(category, List.of());
        for (int i = 0; i < pp.size(); i++) pathOrder.put(pp.get(i).get("domain"), i);
        List<Kp> out = new ArrayList<>(kps);
        var zh = KbScanner.zhCollator();
        java.util.Comparator<String> collator = zh::compare;
        out.sort((a, b) -> {
            int da = pathOrder.getOrDefault(a.domain, 999);
            int db = pathOrder.getOrDefault(b.domain, 999);
            if (da != db) return da - db;
            int c = compareNaturalNull(a.chapter, b.chapter, collator);
            if (c != 0) return c;
            return compareNaturalNull(a.title, b.title, collator);
        });
        return out;
    }

    /** 中文 Collator + 数字段自然序（对齐 localeCompare(..., {numeric:true})） */
    static int compareNaturalNull(String a, String b, Comparator<String> collator) {
        if (a == null) a = "";
        if (b == null) b = "";
        return collator.compare(a, b);
    }

    /** 支持数字段自然比较的比较器（"10" > "9"） */
    static Comparator<String> naturalCollator() {
        var base = KbScanner.zhCollator();
        return (a, b) -> {
            int ia = 0, ib = 0;
            while (ia < a.length() && ib < b.length()) {
                char ca = a.charAt(ia), cb = b.charAt(ib);
                if (Character.isDigit(ca) && Character.isDigit(cb)) {
                    int ea = ia, eb = ib;
                    while (ea < a.length() && Character.isDigit(a.charAt(ea))) ea++;
                    while (eb < b.length() && Character.isDigit(b.charAt(eb))) eb++;
                    int cmp = compareNumeric(a.substring(ia, ea), b.substring(ib, eb));
                    if (cmp != 0) return cmp;
                    ia = ea; ib = eb;
                } else {
                    int cmp = base.compare(String.valueOf(ca), String.valueOf(cb));
                    if (cmp != 0) return cmp;
                    ia++; ib++;
                }
            }
            return (a.length() - ia) - (b.length() - ib);
        };
    }

    private static int compareNumeric(String na, String nb) {
        String sa = na.replaceFirst("^0+(?!$)", "");
        String sb = nb.replaceFirst("^0+(?!$)", "");
        if (sa.length() != sb.length()) return sa.length() - sb.length();
        return sa.compareTo(sb);
    }

    // ---------- 扫描 ----------

    private KnowledgeBase scan() {
        KnowledgeBase kb = new KnowledgeBase();
        kb.root = props.kbRoot();
        Path root = props.kbRootPath();
        if (!Files.isDirectory(root)) {
            kb.exists = false;
            log.warn("[kb] 知识库根目录不存在：{}", root);
            return kb;
        }
        kb.exists = true;
        List<Map<String, Object>> domains = new ArrayList<>();
        List<Kp> kps = new ArrayList<>();
        var collator = KbScanner.zhCollator();
        List<Path> dirs;
        try (Stream<Path> s = Files.list(root)) {
            dirs = s.filter(Files::isDirectory)
                    .filter(p -> !p.getFileName().toString().equals("assets"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(), collator))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path domainDir : dirs) {
            String domainName = domainDir.getFileName().toString();
            List<DocFile> docs = walk(domainDir, domainName);
            String category = KbScanner.classifyDomain(domainName);
            docs.sort(Comparator.comparing(d -> d.rel, collator));
            domains.add(com.closeloop.common.Utils.m(
                    "name", domainName, "category", category, "docs", docs.size(),
                    "status", docs.isEmpty() ? "empty" : docs.size() < 3 ? "building" : "done"));

            Map<String, List<Map<String, Object>>> assetCache = new LinkedHashMap<>();
            for (int i = 0; i < docs.size(); i++) {
                DocFile d = docs.get(i);
                String text;
                try {
                    text = Files.readString(d.abs, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    continue;
                }
                String head = text.length() > 8000 ? text.substring(0, 8000) : text;
                Map<String, Object> meta = KbScanner.parseFrontmatter(head);
                List<Map<String, Object>> anchors = KbScanner.extractAnchors(head);
                String relDir = parentRel(d.rel);
                String chapter = relDir.isEmpty() ? "" : relPathChapters(relDir);
                String baseName = stripExt(lastSegment(d.rel)).replaceFirst("^\\d+[-_]?", "");
                String id = meta.get("id") != null && !String.valueOf(meta.get("id")).isEmpty()
                        ? KbScanner.sanitizeId(String.valueOf(meta.get("id")))
                        : KbScanner.sanitizeId(domainName + "-" + chapter.replace("/", "-") + "-" + (i + 1));
                String title = meta.get("title") != null && !String.valueOf(meta.get("title")).isEmpty()
                        ? String.valueOf(meta.get("title"))
                        : !baseName.isEmpty() ? baseName : d.rel;

                List<Map<String, Object>> allAssets = collectAssets(assetCache, domainDir, chapter);
                String chapterNum = firstMatch("^\\d+", chapter);
                List<DocFile> sameDir = docs.stream().filter(x -> parentRel(x.rel).equals(relDir)).toList();
                boolean multi = sameDir.size() > 1;
                List<Map<String, Object>> assets = new ArrayList<>();
                for (Map<String, Object> a : allAssets) {
                    String file = String.valueOf(a.get("file"));
                    if (multi) {
                        Matcher m = Pattern.compile("^" + (chapterNum != null ? chapterNum + "-" : "") + "(\\d+)").matcher(file);
                        if (m.find()) {
                            DocFile target = sameDir.stream()
                                    .filter(x -> m.group(1).equals(firstMatch("^\\d+", lastSegment(x.rel))))
                                    .findFirst().orElse(null);
                            if (target != null && target != d) continue;
                        }
                    }
                    Map<String, Object> copy = new LinkedHashMap<>(a);
                    copy.put("rel", domainName + "/" + chapter + "/assets/" + file);
                    assets.add(copy);
                }

                Kp kp = new Kp();
                kp.id = id;
                kp.domain = domainName;
                kp.chapter = chapter;
                kp.title = title;
                kp.difficulty = meta.get("difficulty") instanceof Number n ? n.intValue() : 3;
                kp.hot = meta.get("interview_hot") instanceof Number n ? n.intValue() : 3;
                kp.prerequisites = stringList(meta.get("prerequisites"));
                kp.diagrams = stringList(meta.get("diagrams"));
                kp.assets = assets;
                kp.path = d.rel.replace('\\', '/');
                kp.anchors = anchors;
                kp.kbStatus = "done";
                kp.category = category;
                kps.add(kp);
            }
        }
        // id 去重（重复追加 -2/-3）
        Set<String> seen = new LinkedHashSet<>();
        List<Kp> uniq = new ArrayList<>();
        for (Kp kp : kps) {
            String id = kp.id;
            int n = 2;
            while (seen.contains(id)) id = kp.id + "-" + n++;
            seen.add(id);
            kp.id = id;
            uniq.add(kp);
        }
        kb.domains = domains;
        kb.kps = uniq;
        kb.totalDocs = uniq.size();
        return kb;
    }

    /** relDir.split(sep).slice(1).join('/') || relDir（保留 Node 版语义） */
    private static String relPathChapters(String relDir) {
        String[] parts = relDir.replace('\\', '/').split("/");
        if (parts.length <= 1) return relDir.replace('\\', '/');
        return String.join("/", java.util.Arrays.copyOfRange(parts, 1, parts.length));
    }

    private static List<String> stringList(Object v) {
        List<String> out = new ArrayList<>();
        if (v instanceof List<?> l) for (Object o : l) out.add(String.valueOf(o));
        return out;
    }

    private static String firstMatch(String regex, String in) {
        Matcher m = Pattern.compile(regex).matcher(in == null ? "" : in);
        return m.find() ? m.group() : null;
    }

    private static String lastSegment(String rel) {
        String s = rel.replace('\\', '/');
        int i = s.lastIndexOf('/');
        return i < 0 ? s : s.substring(i + 1);
    }

    private static String parentRel(String rel) {
        String s = rel.replace('\\', '/');
        int i = s.lastIndexOf('/');
        return i < 0 ? "" : s.substring(0, i);
    }

    private static String stripExt(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".md") ? name.substring(0, name.length() - 3) : name;
    }

    private record DocFile(Path abs, String rel) {}

    private List<DocFile> walk(Path dir, String base) {
        List<DocFile> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> entries = s.sorted().toList();
            for (Path p : entries) {
                String name = p.getFileName().toString();
                if (name.equals("assets") || name.equals("samples")) continue;
                if (Files.isDirectory(p)) {
                    out.addAll(walk(p, base.isEmpty() ? name : base + "/" + name));
                } else if (name.toLowerCase(Locale.ROOT).endsWith(".md")) {
                    out.add(new DocFile(p, base.isEmpty() ? p.getFileName().toString() : base + "/" + name));
                }
            }
        } catch (IOException e) {
            // 单目录读取失败不影响整体扫描
        }
        return out;
    }

    private List<Map<String, Object>> collectAssets(Map<String, List<Map<String, Object>>> cache, Path domainDir, String chapter) {
        return cache.computeIfAbsent(chapter, ch -> {
            List<Map<String, Object>> list = new ArrayList<>();
            Path ad = domainDir.resolve(ch.isEmpty() ? "." : ch).resolve("assets");
            if (Files.isDirectory(ad)) {
                try (Stream<Path> s = Files.list(ad)) {
                    for (Path f : s.filter(Files::isRegularFile).toList()) {
                        String name = f.getFileName().toString();
                        String ext = name.toLowerCase(Locale.ROOT).contains(".")
                                ? "." + name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
                        if (!ASSET_EXT.contains(ext)) continue;
                        String kind = ext.equals(".html") ? "html" : ext.equals(".svg") ? "svg" : "img";
                        list.add(com.closeloop.common.Utils.m(
                                "name", name.substring(0, name.length() - ext.length()), "file", name, "kind", kind));
                    }
                } catch (IOException e) {
                    // 无 assets 目录
                }
                var collator = KbScanner.zhCollator();
                list.sort(Comparator.comparing(a -> String.valueOf(a.get("file")), collator));
            }
            return list;
        });
    }
}
