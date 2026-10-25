import { defineConfig } from 'vitepress'

// docs.vuegram.com. Dark only, like the app; local search; no analytics or third-party scripts.
export default defineConfig({
  lang: 'en',
  title: 'VueGram Docs',
  titleTemplate: ':title · VueGram',
  description:
    'How to install, log in to and use VueGram, the open-source Android TV app for your Telegram groups and channels.',
  cleanUrls: true,
  appearance: 'force-dark',
  lastUpdated: false,
  sitemap: { hostname: 'https://docs.vuegram.com' },

  head: [
    ['link', { rel: 'icon', href: '/favicon.svg', type: 'image/svg+xml' }],
    ['meta', { name: 'theme-color', content: '#000000' }],
    ['meta', { property: 'og:site_name', content: 'VueGram Docs' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.googleapis.com' }],
    ['link', { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' }],
    [
      'link',
      {
        rel: 'stylesheet',
        href: 'https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600&display=swap'
      }
    ]
  ],

  themeConfig: {
    logo: { src: '/logo.svg', alt: '' },
    siteTitle: 'VueGram',

    nav: [
      { text: 'Guide', link: '/guide/getting-started', activeMatch: '/guide/' },
      { text: 'Developers', link: '/developers/building', activeMatch: '/developers/' },
      { text: 'FAQ', link: '/guide/faq' },
      { text: 'vuegram.com', link: 'https://vuegram.com' },
      { text: 'Download', link: 'https://github.com/vuegram/vuegram/releases' }
    ],

    sidebar: {
      '/guide/': [
        {
          text: 'Start here',
          items: [
            { text: 'Getting started', link: '/guide/getting-started' },
            { text: 'Logging in', link: '/guide/logging-in' }
          ]
        },
        {
          text: 'Using VueGram',
          items: [
            { text: 'Library, chats and player', link: '/guide/using-vuegram' },
            { text: 'Settings', link: '/guide/settings' },
            { text: 'Updates', link: '/guide/updates' }
          ]
        },
        {
          text: 'Help',
          items: [
            { text: 'Privacy & security', link: '/guide/privacy' },
            { text: 'Troubleshooting', link: '/guide/troubleshooting' },
            { text: 'FAQ', link: '/guide/faq' }
          ]
        },
        {
          text: 'More',
          items: [{ text: 'For developers', link: '/developers/building' }]
        }
      ],
      '/developers/': [
        {
          text: 'For developers',
          items: [
            { text: 'Building from source', link: '/developers/building' },
            { text: 'TDLib, built from source', link: '/developers/tdlib' },
            { text: 'Releasing', link: '/developers/releasing' },
            { text: 'Project layout and rules', link: '/developers/project' },
            { text: 'Contributing', link: '/developers/contributing' }
          ]
        },
        {
          text: 'User guide',
          items: [{ text: 'Back to the guide', link: '/guide/getting-started' }]
        }
      ]
    },

    search: { provider: 'local' },

    socialLinks: [{ icon: 'github', link: 'https://github.com/vuegram/vuegram' }],

    outline: { level: [2, 3], label: 'On this page' },

    docFooter: { prev: 'Previous', next: 'Next' },

    footer: {
      message:
        'VueGram is an unofficial app. It is not made by, affiliated with or endorsed by Telegram. No cookies, no analytics.',
      copyright: 'MIT License · VueGram contributors'
    }
  }
})
