import { defineUserConfig, defaultTheme } from 'vuepress'

export default defineUserConfig({
	base: '/',
	locales: {
		'/': {
			lang: 'en-US',
			title: 'Decrel',
			description: 'Composable relations for Scala',
		},
		//'/ko/': {
		//	lang: 'ko-KR',
		//}
	},
	theme: defaultTheme({
		repo: 'yoohaemin/decrel',
		locales: {
			'/': {
				navbar: [{
					text: 'Home',
					link: '/',
				},{
					text: 'Guide',
					link: '/guide/index.html'
				},{
					text: 'Showcase',
					link: '/showcase/index.html'
				}],
				selectLanguageName: 'English',
				sidebar: {
					'/guide/': [{
						text: 'Guide',
						children: [
							'/guide/README.md',
							'/guide/getting-started.md',
							'/guide/defining-relations.md'
						],
					}],
					//'/reference/': [
					//  {
					//    text: 'Reference',
					//    children: ['/reference/cli.md', '/reference/config.md'],
					//  },
					//],
				},



			},
			//'/ko/': {
			//	selectLanguageName: '한국어',
			//},
		},
	}),
	markdown: {
		code: {
			lineNumbers: false
		}
	}
})
