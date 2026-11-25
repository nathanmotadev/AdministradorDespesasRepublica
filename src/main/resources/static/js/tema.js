// Tema claro/escuro. A preferência fica no navegador; "auto" acompanha o sistema do aparelho.
import { transicao } from './movimento.js';

const CHAVE = 'republica.tema';
const sistemaEscuro = window.matchMedia('(prefers-color-scheme: dark)');
const ouvintes = new Set();

export function preferenciaTema() {
    try { return localStorage.getItem(CHAVE) || 'auto'; } catch { return 'auto'; }
}

/** O tema que está na tela agora: "claro" ou "escuro". */
export const temaAtual = () => document.documentElement.dataset.tema;

function aplicar() {
    const preferencia = preferenciaTema();
    const escuro = preferencia === 'escuro' || (preferencia === 'auto' && sistemaEscuro.matches);
    const tema = escuro ? 'escuro' : 'claro';
    const mudar = () => {
        document.documentElement.dataset.tema = tema;
        document.querySelector('meta[name="theme-color"]')?.setAttribute('content', escuro ? '#151311' : '#ece7e3');
        ouvintes.forEach((ouvinte) => ouvinte(tema));
    };
    if (tema === temaAtual()) mudar(); // abrindo o app, ou a preferência mudou sem mudar as cores
    else transicao(mudar);
}

/** preferencia: "claro", "escuro" ou "auto". */
export function definirTema(preferencia) {
    try { localStorage.setItem(CHAVE, preferencia); } catch { /* vale só até fechar a aba */ }
    aplicar();
}

/** Troca direto entre claro e escuro (botão do topo). */
export const alternarTema = () => definirTema(temaAtual() === 'escuro' ? 'claro' : 'escuro');

export function aoMudarTema(ouvinte) {
    ouvintes.add(ouvinte);
    return () => ouvintes.delete(ouvinte);
}

sistemaEscuro.addEventListener('change', () => { if (preferenciaTema() === 'auto') aplicar(); });
aplicar();
