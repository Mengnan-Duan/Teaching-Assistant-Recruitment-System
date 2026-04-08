import subprocess

# Get clean content from git HEAD
result = subprocess.run(
    ['git', '-C', 'C:/Users/12466/Desktop/Software Engineering/Teaching-Assistant-Recruitment-System',
     'show', 'HEAD:SmartTA/src/main/java/com/bupt/smartta/servlet/ApiServlet.java'],
    capture_output=True, text=True, encoding='utf-8'
)
if result.returncode != 0:
    print(f"ERROR: git failed: {result.stderr}")
    exit(1)

content = result.stdout
line_count = len(content.splitlines())
print(f"Git HEAD has {line_count} lines")

# Write to both locations
dst = r'D:\Tomcat\apache-tomcat-10.1.48\webapps\SmartTA\WEB-INF\classes\com\bupt\smartta\servlet\ApiServlet.java'
with open(dst, 'w', encoding='utf-8') as f:
    f.write(content)
print(f"Written to {dst}")

dst2 = r'D:\Tomcat\apache-tomcat-10.1.48\webapps\SmartTA\src\main\java\com\bupt\smartta\servlet\ApiServlet.java'
with open(dst2, 'w', encoding='utf-8') as f:
    f.write(content)
print(f"Written to {dst2}")

# Now add the config case
content = content.replace(
    '            case "score":',
    '            case "config":\n                sb.append(systemConfigToJson(ds.getSystemConfig()));\n                break;\n            case "score":'
)

# Now add the systemConfigToJson method before logToJson
config_method = '''
    // ---- System Config JSON Serializer ----

    private String systemConfigToJson(SystemConfig cfg) {
        if (cfg == null) return "{}";
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\\\"appVersion\\\":\\\"").append(esc(cfg.getAppVersion())).append("\\\",");
        sb.append("\\\"buildDate\\\":\\\"").append(esc(cfg.getBuildDate())).append("\\\",");
        sb.append("\\\"demoAccounts\\\":[");
        if (cfg.getDemoAccounts() != null) {
            for (int i = 0; i < cfg.getDemoAccounts().size(); i++) {
                SystemConfig.DemoAccount a = cfg.getDemoAccounts().get(i);
                if (i > 0) sb.append(",");
                sb.append("{");
                sb.append("\\\"username\\\":\\\"").append(esc(a.getUsername())).append("\\\",");
                sb.append("\\\"password\\\":\\\"").append(esc(a.getPassword())).append("\\\",");
                sb.append("\\\"role\\\":\\\"").append(esc(a.getRole())).append("\\\",");
                sb.append("\\\"displayName\\\":\\\"").append(esc(a.getDisplayName())).append("\\\"}");
            }
        }
        sb.append("],");
        sb.append("\\\"versionHistory\\\":[");
        if (cfg.getVersionHistory() != null) {
            for (int i = 0; i < cfg.getVersionHistory().size(); i++) {
                SystemConfig.VersionEntry v = cfg.getVersionHistory().get(i);
                if (i > 0) sb.append(",");
                sb.append("{");
                sb.append("\\\"version\\\":\\\"").append(esc(v.getVersion())).append("\\\",");
                sb.append("\\\"date\\\":\\\"").append(esc(v.getDate())).append("\\\",");
                sb.append("\\\"title\\\":\\\"").append(esc(v.getTitle())).append("\\\",");
                sb.append("\\\"description\\\":\\\"").append(esc(v.getDescription())).append("\\\"}");
            }
        }
        sb.append("],");
        sb.append("\\\"featureCoverage\\\":[");
        if (cfg.getFeatureCoverage() != null) {
            for (int i = 0; i < cfg.getFeatureCoverage().size(); i++) {
                SystemConfig.FeatureCoverage f = cfg.getFeatureCoverage().get(i);
                if (i > 0) sb.append(",");
                sb.append("{");
                sb.append("\\\"icon\\\":\\\"").append(esc(f.getIcon())).append("\\\",");
                sb.append("\\\"text\\\":\\\"").append(esc(f.getText())).append("\\\"}");
            }
        }
        sb.append("],");
        sb.append("\\\"fileStatusConfig\\\":[");
        if (cfg.getFileStatusConfig() != null) {
            for (int i = 0; i < cfg.getFileStatusConfig().size(); i++) {
                SystemConfig.FileStatusConfig f = cfg.getFileStatusConfig().get(i);
                if (i > 0) sb.append(",");
                sb.append("{");
                sb.append("\\\"filename\\\":\\\"").append(esc(f.getFilename())).append("\\\",");
                sb.append("\\\"displayName\\\":\\\"").append(esc(f.getDisplayName())).append("\\\",");
                sb.append("\\\"category\\\":\\\"").append(esc(f.getCategory())).append("\\\"}");
            }
        }
        sb.append("],");
        if (cfg.getWorkloadConfig() != null) {
            SystemConfig.WorkloadConfig wc = cfg.getWorkloadConfig();
            sb.append("\\\"workloadConfig\\\":{");
            sb.append("\\\"capacity\\\":").append(wc.getCapacity()).append(",");
            sb.append("\\\"overloadThreshold\\\":").append(wc.getOverloadThreshold()).append(",");
            sb.append("\\\"overloadUnit\\\":\\\"").append(esc(wc.getOverloadUnit())).append("\\\"},");
        } else { sb.append("\\\"workloadConfig\\\":{},"); }
        if (cfg.getPositionDefaults() != null) {
            SystemConfig.PositionDefaults pd = cfg.getPositionDefaults();
            sb.append("\\\"positionDefaults\\\":{");
            sb.append("\\\"defaultHours\\\":").append(pd.getDefaultHours()).append(",");
            sb.append("\\\"defaultSlots\\\":").append(pd.getDefaultSlots()).append(",");
            sb.append("\\\"defaultDeadline\\\":\\\"").append(esc(pd.getDefaultDeadline())).append("\\\",");
            sb.append("\\\"defaultPostedBy\\\":\\\"").append(esc(pd.getDefaultPostedBy())).append("\\\"},");
        } else { sb.append("\\\"positionDefaults\\\":{},"); }
        sb.append("\\\"skillSuggestions\\\":[");
        if (cfg.getSkillSuggestions() != null) {
            for (int i = 0; i < cfg.getSkillSuggestions().size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\\\"\\\"").append(esc(cfg.getSkillSuggestions().get(i))).append("\\\"\\\"");
            }
        }
        sb.append("],");
        if (cfg.getDataTraceability() != null) {
            SystemConfig.DataTraceability dt = cfg.getDataTraceability();
            sb.append("\\\"dataTraceability\\\":{");
            sb.append("\\\"positions\\\":\\\"").append(esc(dt.getPositions())).append("\\\",");
            sb.append("\\\"applications\\\":\\\"").append(esc(dt.getApplications())).append("\\\",");
            sb.append("\\\"applicants\\\":\\\"").append(esc(dt.getApplicants())).append("\\\",");
            sb.append("\\\"workloads\\\":\\\"").append(esc(dt.getWorkloads())).append("\\\",");
            sb.append("\\\"users\\\":\\\"").append(esc(dt.getUsers())).append("\\\",");
            sb.append("\\\"logs\\\":\\\"").append(esc(dt.getLogs())).append("\\\",");
            sb.append("\\\"cvs\\\":\\\"").append(esc(dt.getCvs())).append("\\\"}");
        } else { sb.append("\\\"dataTraceability\\\":{}"); }
        sb.append("}");
        return sb.toString();
    }

'''

content = content.replace('    private String logToJson(SystemLog l) {', config_method + '    private String logToJson(SystemLog l) {')

# Write final version
with open(dst, 'w', encoding='utf-8') as f:
    f.write(content)
with open(dst2, 'w', encoding='utf-8') as f:
    f.write(content)

# Verify
final_line_count = len(content.splitlines())
print(f"Final file has {final_line_count} lines")
print(f"case 'config' count: {content.count('case \"config\":')}")
print(f"handleRebalanceWorkload count: {content.count('private void handleRebalanceWorkload(StringBuilder')}")
print(f"systemConfigToJson count: {content.count('private String systemConfigToJson')}")
