const colors = require('tailwindcss/colors')

module.exports = {
    content: ['./src/**/*.{ts,tsx}'],
    theme: {
        fontFamily: {
            sans: ['Martian Mono', 'sans-serif']
        },
        fontSize: {
            xxxs: '.4rem',
            xxs: '.6rem',
            xs: '.75rem',
            sm: '.875rem',
            tiny: '.875rem',
            base: '1rem',
            lg: '1.125rem',
            xl: '1.25rem',
            '2xl': '1.5rem',
            '3xl': '1.875rem',
            '4xl': '2.25rem',
            '5xl': '3rem',
            '6xl': '4rem',
            '7xl': '5rem'
        },
        extend: {
            transitionProperty: {
                'max-height': 'max-height'
            },
            boxShadow: {
                centered: '0 0 10px 1px rgba(0,0,0,0.6)'
            },
            colors: {
                errorred: '#ff9194',
                team0: 'var(--color-team0)',
                team1: 'var(--color-team1)',

                walls: 'var(--color-walls)',
                tile: 'var(--color-tile)',
                gameareaBackground: 'var(--color-gamearea-background)',
                sidebarBackground: 'var(--color-sidebar-background)',

                red: 'var(--color-red)',
                pink: 'var(--color-pink)',
                green: 'var(--color-green)',
                cyan: 'var(--color-cyan)',
                cyanDark: 'var(--color-cyan-dark)',
                blue: 'var(--color-blue)',
                blueLight: 'var(--color-blue-light)',
                blueDark: 'var(--color-blue-dark)',

                dark: 'var(--color-dark)',
                darkHighlight: 'var(--color-dark-highlight)',
                black: 'var(--color-black)',
                white: 'var(--color-white)',
                light: 'var(--color-light)',
                lightHighlight: 'var(--color-light-highlight)',
                medHighlight: 'var(--color-med-highlight)',
                lightCard: 'var(--color-light-card)'
            }
        }
    },
    plugins: []
}
