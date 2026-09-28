workspace "学习闭环系统 Java 后端（server-java）" "面试知识闭环学习系统 Spring Boot 重构版 · 当前态 C4 模型 · 生成日期 2026-09-19 · system-modeler + c4model" {

  model {
    # ---------- 人物与外部系统（L1） ----------
    learner = person "学习者（唯一用户）" "单机自用：按系统排期刷题、答题、复盘项目面试"

    reactClient = softwareSystem "React 前端（client/）" "Vite + React SPA，调用 /api/**，契约 snake_case，零改动直连" {
      tags "Frontend"
    }
    aiApi = softwareSystem "AI 服务（OpenAI 兼容）" "api.agnes-ai.cn /v1/chat/completions，agnes-2.5-flash" {
      tags "External,Ai"
    }
    kbTree = softwareSystem "后端知识库（Markdown 文件树）" "02-技术学习资料/后端知识库：域/章/文档+assets，只读扫描" {
      tags "External,FileSystem"
    }
    nodeLegacy = softwareSystem "Node 版后端（server/）" "既有实现，与本系统共享同一数据文件，互不修改代码" {
      tags "External,Legacy"
    }

    # ---------- 本系统（L2/L3） ----------
    closeloop = softwareSystem "面试知识闭环学习系统" "高内聚低耦合的 Java 前后端分离重构实现（server-java）" {

      apiApp = container "Java 后端应用" "Spring Boot 3.3.5 / Java 17，端口 3002，Maven 单模块" "Spring Boot" {

        webC = component "web 契约层" "8 个 Controller + RolloverInterceptor（每请求 ensureToday）+ Req 参数助手；仅校验与组装" "Spring MVC" {
          tags "Contract"
        }
        svcC = component "service 编排层" "11 个业务服务：Question/Grading/Restate/KpPlan/Gap/Curve/Weekly/Lc/StateView/ProjectInterview/MockInterview" "Spring @Service" {
          tags "Orchestration"
        }
        learnC = component "learning 机制内核" "Sm2 调度（SM-2+FSRS）/ Priority / QueueBuilder 队列组装 / AnswerApplier 作答回写 / Kpi；纯函数，不依赖 web/ai" "Java" {
          tags "Domain"
        }
        stC = component "state 持久化层" "StateManager（ReentrantLock 生成锁 + mutate 读改写落盘）/ StateRepository（tmp+atomic rename，损坏备份）/ model 持久化 DTO" "Jackson" {
          tags "State"
        }
        aiC = component "ai 工程化层" "AiClient（JDK HttpClient，指数退避，AUTH 不重试）/ JsonExtractor（fence+配平+尾逗号）/ chatJson 3 轮自修复 / prompt 模板库（Kb/Lc/Project/Mock Prompts + Dims + Tpl）" "Java" {
          tags "AiInfra"
        }
        kbC = component "knowledge 只读层" "KbService（域扫描/注册表缓存/锚点/assets 归属）/ KbScanner（frontmatter/中文自然序）/ LcDataService（lc100 + solutions）" "Java NIO" {
          tags "ReadOnlyData"
        }
        projC = component "project 数据层" "ResumeData：简历结构、技能组、模块切面 f-all/f1-f5、深挖语境，编译期常量" "Java" {
          tags "ReadOnlyData"
        }
        cfgC = component "config + common 基础设施" "AppProperties / AiProperties / WebConfig(CORS+拦截器) / StaticResourceConfig(dist 托管) / Utils / Dates / ApiException / GlobalExceptionHandler" "Spring" {
          tags "Infra"
        }

        webC -> svcC "调用业务用例"
        svcC -> learnC "队列组装/评分回写/指标计算"
        svcC -> stC "mutate / 读取状态"
        svcC -> aiC "chatJson / chat（全部 AI 出口）"
        svcC -> kbC "读知识文档/力扣数据"
        svcC -> projC "读简历与切面"
        learnC -> stC "读写 state.model DTO"
        stC -> kbC "启动合并：把 KB 知识点元信息并入 kpStates（inferred-on-boot）" {
          tags "Exception"
        }
        aiC -> stC "prompt 模板引用持久化 DTO 类型（只读，不改动状态）" {
          tags "Exception"
        }
        aiC -> projC "ResumeContext/Mock/ProjectPrompts 引用 Facet 等类型" {
          tags "Exception"
        }
        aiC -> kbC "LcPrompts 引用 LcProblem 类型" {
          tags "Exception"
        }
        webC -> stC "拦截器每请求 ensureToday()"
        cfgC -> webC "WebConfig 注册 RolloverInterceptor" {
          tags "Exception"
        }
      }

      stateJson = container "state.json（v2）" "全局学习状态唯一事实源：settings/kps/cursor/queue/genSessions/gapTests/lcExplains/sessionToday/sessions/gaps/records/project/kpi；Node/Java 共享" "JSON 文件" {
        tags "Database"
      }
      lcStore = container "lc100.json / lc-solutions.json" "力扣 101 题题面与题解（只读）" "JSON 文件" {
        tags "Database"
      }
      resumeHtml = container "resume_upload.html" "简历 HTML，前端 iframe 预览（只读）" "HTML 文件" {
        tags "Database"
      }
      distDir = container "client/dist" "生产模式下由本应用静态托管的前端构建产物（可缺省）" "目录" {
        tags "Database"
      }
    }

    # ---------- 跨系统关系 ----------
    learner -> reactClient "日常学习"
    reactClient -> apiApp "HTTP JSON /api/**（开发经 Vite 代理→3002；生产同源）"
    apiApp -> aiApi "POST /chat/completions（超时 180s；500ms×2^n+抖动重试≤3）"
    apiApp -> kbTree "扫描/读取 .md 与 assets（只读，kbTextLimit=6000 截断）"
    apiApp -> stateJson "读 + 原子写（tmp→rename）"
    apiApp -> lcStore "读"
    apiApp -> resumeHtml "读"
    apiApp -> distDir "静态托管（存在才挂载）"
    nodeLegacy -> stateJson "读写同一文件（双后端进度互通；并发窗口为已知假设，见 evidence）" {
      tags "SharedData"
    }
    nodeLegacy -> lcStore "读写（Java 侧只读）" {
      tags "SharedData"
    }
  }

  views {
    systemContext closeloop "C1SystemContext" "系统上下文：学习者/前端/AI 服务/知识库/Node 遗留后端与本系统边界" {
      include *
      autoLayout
    }

    container closeloop "C2Containers" "容器视图：进程、共享数据文件、外部集成" {
      include *
      autoLayout tb
    }

    component apiApp "C3Components" "组件视图：八个包的真实依赖（含三条受控例外边，tag=Exception）" {
      include *
      autoLayout tb
    }

    styles {
      element "Person" {
        shape person
        background #08427b
        color #ffffff
      }
      element "Frontend" {
        background #438dd5
        color #ffffff
      }
      element "External" {
        background #999999
        color #ffffff
      }
      element "Ai" {
        background #8a2be2
        color #ffffff
      }
      element "Legacy" {
        background #b0b0b0
        fontStyle italic
      }
      element "Database" {
        shape cylinder
        background #4d642f
        color #ffffff
      }
      element "Contract" { background #2f6cb3 }
      element "Orchestration" { background #3b8ed0 }
      element "Domain" { background #083f5d }
      element "State" { background #6c4f00 }
      element "AiInfra" { background #6a1b9a }
      element "ReadOnlyData" { background #4d642f }
      element "Infra" { background #5b5b5b }
      element "FileSystem" { shape folder }
      relationship "Exception" {
        style dashed
        color #c62828
      }
      relationship "SharedData" {
        style dashed
        color #e07b00
      }
    }
  }
}
