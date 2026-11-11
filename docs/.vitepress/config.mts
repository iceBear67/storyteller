import { defineConfig } from 'vitepress'

export default defineConfig({
  lang: 'zh-CN',
  title: 'Storyteller',
  description: '用数据包脚本驱动的 Minecraft 对话系统',
  themeConfig: {
    nav: [
      { text: '教程', link: '/tutorial/setup' },
      { text: '参考', link: '/reference/syntax' }
    ],
    sidebar: {
      '/tutorial/': [
        {
          text: '教程',
          items: [
            { text: '安装与第一段对话', link: '/tutorial/setup' },
            { text: '对话脚本', link: '/tutorial/scripting' },
            { text: '跟原版命令交互', link: '/tutorial/vanilla-commands' },
            { text: '触发实体', link: '/tutorial/triggers' },
            { text: '调试与排错', link: '/tutorial/debugging' },
            { text: '实战教程：村口任务', link: '/tutorial/quest' }
          ]
        }
      ],
      '/reference/': [
        {
          text: '参考',
          items: [
            { text: '脚本文法', link: '/reference/syntax' },
            { text: '脚本命令', link: '/reference/commands' },
            { text: '条件表达式', link: '/reference/conditions' },
            { text: '状态与存档', link: '/reference/state' },
            { text: '对话运行', link: '/reference/runtime' },
            { text: '触发实体', link: '/reference/entities' },
            { text: '工具链', link: '/reference/toolchain' }
          ]
        }
      ]
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/iceBear67/storyteller' }
    ]
  }
})
