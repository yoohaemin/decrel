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
							'/guide/defining-relations.md',

							'/guide/zquery.md', // Proofs, contramaps, ...
							'/guide/fetch.md', // Proofs, contramaps, ...
							'/guide/scalacheck.md', // Proofs, contramaps, ...
							'/guide/ziotest.md', // Proofs, contramaps, ...

							'/guide/callsites.md', //
							'/guide/core-concepts.md',
							'/guide/advanced.md', // Advanced
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
