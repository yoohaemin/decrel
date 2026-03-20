import { viteBundler } from '@vuepress/bundler-vite'
import { defaultTheme } from '@vuepress/theme-default'
import { defineUserConfig } from 'vuepress'

export default defineUserConfig({
  bundler: viteBundler(),
  theme: defaultTheme({
    repo: 'yoohaemin/decrel',
    locales: {
      '/': {
        navbar: [
          {
            text: 'Home',
            link: '/'
          },
          {
            text: 'Guide',
            link: '/guide/'
          },
          {
            text: 'Example App',
            link: '/example-app/'
          },
          {
            text: 'Reference',
            link: '/reference/'
          },
          {
            text: 'API',
            link: '/reference/api-reference.html'
          }
        ],
        selectLanguageName: 'English',
        sidebar: {
          '/guide/': [
            {
              text: 'Guide',
              children: [
                '/guide/README.md',
                '/guide/production-readiness.md',
                '/guide/getting-started.md',
                '/guide/defining-relations.md',
                '/guide/composition-of-joins.md',
                '/guide/implementing-proofs.md',
                '/guide/cache-and-custom-relations.md',
                '/guide/testing.md'
              ]
            }
          ],
          '/example-app/': [
            {
              text: 'Example App',
              children: [
                '/example-app/README.md'
              ]
            }
          ],
          '/reference/': [
            {
              text: 'Reference',
              children: [
                '/reference/README.md',
                '/reference/module-matrix.md',
                '/reference/advanced-apis.md',
                '/reference/api-reference.md'
              ]
            }
          ]
        }
      }
    }
  }),
  lang: 'en-US',
  title: 'Decrel',
  description: 'Compose joins declaratively in Scala.',
  base: '/',
  locales: {
    '/': {
      lang: 'en-US',
      title: 'Decrel',
      description: 'Compose joins declaratively in Scala.'
    }
  },
  markdown: {
    code: {
      lineNumbers: false
    }
  }
})
