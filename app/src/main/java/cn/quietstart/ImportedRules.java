package cn.quietstart;

import java.io.*;
import java.util.*;

/** DNS imports; unsupported rules are counted for explicit partial-import confirmation. */
public final class ImportedRules {
    public static final int LIMIT=10*1024*1024;
    public record Parsed(String text,int count,int skipped,String allowed) {}
    public static Parsed parse(String source) throws IOException {
        Set<String> domains=new LinkedHashSet<>();
        Set<String> allowed=new LinkedHashSet<>();int skipped=0;
        int number=0;
        try(BufferedReader reader=new BufferedReader(new StringReader(source))) {
            String line;
            while((line=reader.readLine())!=null) {
                number++;
                if(number==1&&line.startsWith("\uFEFF"))line=line.substring(1);
                line=line.trim();
                if(line.isEmpty()||line.startsWith("#")||line.startsWith("!"))continue;
                if(line.equals("[Adblock Plus 2.0]"))continue;
                if(line.startsWith("@@")||line.startsWith("||")||line.startsWith("|")) {
                    boolean exception=line.startsWith("@@");String rule=exception?line.substring(2):line;
                    if(rule.matches("\\|\\|[^\\^|/$*]+\\^\\|?")) {
                        String domain=rule.substring(2,rule.indexOf('^'));
                        try{(exception?allowed:domains).add(Rules.normalize(domain));}catch(IllegalArgumentException e){skipped++;}
                    }else skipped++;
                    if(domains.size()+allowed.size()>500000)throw new IOException("规则数量超过 500000 条上限");
                    continue;
                }
                line=line.replaceFirst("\\s+#.*$", "").trim();
                String[] parts=line.split("\\s+");
                int start=0;
                if(parts[0].equals("0.0.0.0")||parts[0].equals("127.0.0.1")||parts[0].equals("::"))start=1;
                else if(parts.length!=1)throw invalid(number);
                if(start==parts.length)throw invalid(number);
                for(int i=start;i<parts.length;i++) {
                    String domain=parts[i];
                    if(start==1&&(domain.equals("localhost")||domain.equals("localhost.localdomain")||domain.equals("ip6-localhost")||domain.equals("ip6-loopback")))continue;
                    if(domain.matches("[0-9.]+")||domain.contains("#")){skipped++;continue;}
                    try {domains.add(Rules.normalize(domain));}catch(IllegalArgumentException e){skipped++;}
                    if(domains.size()+allowed.size()>500000)throw new IOException("规则数量超过 500000 条上限");
                }
            }
        }
        if(domains.isEmpty())throw new IOException("文件没有可用的域名规则");
        String exceptions=String.join("\n",allowed);
        StringBuilder text=new StringBuilder(String.join("\n",domains)).append('\n');
        for(String domain:allowed)text.append("@@||").append(domain).append("^\n");
        if(text.length()>LIMIT)throw new IOException("转换后的规则超过 10 MB 上限");
        return new Parsed(text.toString(),domains.size(),skipped,exceptions);
    }
    private static IOException invalid(int line) {
        return new IOException("第 "+line+" 行不是受支持的域名 / hosts 规则。请选择 DNS 规则文件；不支持浏览器 AdBlock、脚本或路径规则。原规则未改变。");
    }
}
