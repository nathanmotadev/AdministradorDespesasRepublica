// Tela de abertura: a casinha (ou o prédio) com o nome do app, enquanto os dados carregam.
import { h } from './ui.js';
import { imagemMoradia } from './components.js';

const CHAVE_MORADIA = 'republica.moradia';
const TEMPO_MINIMO = 2200; // ms: dá tempo de a moeda pular pelo menos uma vez
const poucoMovimento = window.matchMedia('(prefers-reduced-motion: reduce)');

/** Lembra se a casa é CASA ou APARTAMENTO, para a próxima abertura já mostrar o desenho certo. */
export function lembrarMoradia(tipo) {
    try { localStorage.setItem(CHAVE_MORADIA, tipo); } catch { /* sem armazenamento */ }
}

export function moradiaLembrada() {
    try { return localStorage.getItem(CHAVE_MORADIA) || 'CASA'; } catch { return 'CASA'; }
}

/**
 * Mostra a abertura e devolve uma função "fechar". Ela some quando os dois acontecerem:
 * o tempo mínimo passou e quem abriu chamou fechar() (ou seja, os dados chegaram).
 * fechar() devolve uma Promise que resolve quando a abertura começa a sumir.
 */
export function mostrarAbertura(tipo = moradiaLembrada()) {
    document.querySelector('.abertura')?.remove();
    const animada = tipo !== 'APARTAMENTO' && !poucoMovimento.matches;
    const figura = animada
        ? h('span', { class: 'abertura-figura' },
            // ?v= reinicia o GIF do primeiro quadro toda vez que a abertura aparece
            h('img', { class: 'so-claro', src: `img/abertura-dia.gif?v=${Date.now()}`, alt: '' }),
            h('img', { class: 'so-escuro', src: `img/abertura-noite.gif?v=${Date.now()}`, alt: '' }))
        : imagemMoradia(tipo, 'abertura-figura');

    const tela = h('div', { class: 'abertura', 'aria-hidden': 'true' },
        figura,
        h('p', { class: 'abertura-nome' }, 'república'),
        h('p', { class: 'abertura-texto' }, 'Arrumando a casa...'));
    document.body.append(tela);

    const minimo = poucoMovimento.matches ? 500 : TEMPO_MINIMO;
    const tempo = new Promise((resolve) => setTimeout(resolve, minimo));
    let liberar;
    const pronto = new Promise((resolve) => { liberar = resolve; });

    const saindo = Promise.all([tempo, pronto]).then(() => {
        tela.classList.add('saindo');
        tela.addEventListener('animationend', () => tela.remove(), { once: true });
        setTimeout(() => tela.remove(), 800); // se a animação não rodar, some do mesmo jeito
    });
    return () => { liberar(); return saindo; };
}
