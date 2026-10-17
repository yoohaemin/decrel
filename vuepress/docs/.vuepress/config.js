import {viteBundler} from '@vuepress/bundler-vite'
import {defaultTheme} from '@vuepress/theme-default'
import {defineUserConfig} from 'vuepress'

export default defineUserConfig({
  bundler: viteBundler(),
  theme: defaultTheme({
    repo: 'yoohaemin/decrel',
    locales: {
      '/': {
        navbar: [
          { text: 'Home', link: '/' },
          { text: 'Guide', link: '/guide/' },
          { text: 'Integrations', link: '/guide/integrations.html' },
          { text: 'Testing', link: '/guide/testing.html' },
          { text: 'Showcase', link: '/showcase/' }
        ],
        selectLanguageName: 'English',
        sidebar: {
          '/guide/': [
            { text: 'Core Concepts', children: [
              '/guide/README.md', '/guide/getting-started.md',
              '/guide/defining-relations.md', '/guide/composing-relations.md'
            ] },
            { text: 'Execution & Ecosystems', children: [
              '/guide/integrations.md', '/guide/caching-and-advanced.md', '/guide/testing.md'
            ] }
          ],
          '/showcase/': [{ text: 'Showcase', children: ['/showcase/README.md'] }]
        }
      }
    }
  }),
  lang: 'en-US',
  title: 'decrel',
  description: 'Declarative data relations for Scala',
  base: '/',
  markdown: { code: { lineNumbers: false } }
})
