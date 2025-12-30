import { defaultTheme } from '@vuepress/theme-default'
import { defineUserConfig } from 'vuepress'
import { viteBundler } from '@vuepress/bundler-vite'

export default defineUserConfig({
  lang: 'zh-CN',
  title: 'CivGenesis',
  description: 'Java 21 / Spring Boot / Netty 游戏服务器 SDK（框架代码）',
  base: process.env.VUEPRESS_BASE ?? '/',
  bundler: viteBundler(),

  theme: defaultTheme({
    repo: 'cuihairu/civgenesis',
    docsDir: 'docs',
    docsBranch: 'main',
    editLink: true,

    navbar: [
      { text: '文档', link: '/' },
      { text: '设计', link: '/DESIGN' },
      { text: '协议', link: '/PROTOCOL' },
      { text: 'SDK', link: '/CLIENT_SDK' },
      { text: 'Protobuf', link: '/PROTOBUF' },
      { text: '观测', link: '/OBSERVABILITY' },
      { text: 'Jobs', link: '/JOBS' },
      { text: 'IPC', link: '/IPC' },
      { text: 'Nacos', link: '/REGISTRY_NACOS' },
      { text: 'gRPC', link: '/GRPC' },
      { text: 'Dispatcher', link: '/DISPATCHER' }
    ],
    sidebar: [
      {
        text: '指南',
        children: [
          '/DESIGN',
          '/PROTOCOL',
          '/PROTOBUF',
          '/OBSERVABILITY',
          '/JOBS',
          '/CLIENT_SDK',
          '/DISPATCHER',
          '/IPC',
          '/REGISTRY_NACOS',
          '/GRPC',
          '/SCHEDULER',
          '/CONFIG',
          '/SECURITY',
          '/SYSTEM_MESSAGES',
          '/HOTFIX'
        ]
      }
    ]
  })
})
