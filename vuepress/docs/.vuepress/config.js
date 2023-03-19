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
				},{
					text: 'Contributing',
					link: '/contributing/index.html'
				},{
					text: 'Scaladoc',
					link: '/contributing/index.html'
				}],
				selectLanguageName: 'English',
				sidebar: {
					'/guide/': [{
						text: 'User Guide',
						children: [
							'/guide/README.md',
							'/guide/getting-started.md',
							'/guide/core-concepts.md',
							'/guide/defining-relations.md',
							'/guide/providing-proofs.md', // Proofs, contramaps, ...
							'/guide/callsites.md', //
							'/guide/.md', // Advanced
							'/guide/contributing.md'
						],
					}],
					'/showcase/': [{
						text: 'Showcase',
						children: [
							'/showcase/README.md'
						],
					}],
					'/contributing/': [{
						text: "Contributing",
						children: [
							'/contributing/README.md' //
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
