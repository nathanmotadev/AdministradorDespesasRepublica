// Peças visuais reutilizadas em várias telas.
import { h, toast } from './ui.js';
import { dataBR } from './format.js';

const CORES = ['#0b3d36', '#b45309', '#4f46e5', '#be185d', '#0369a1', '#6d28d9', '#15803d', '#b91c1c'];

export function avatar(morador, pequeno = false) {
    return h('span', {
        class: `avatar${pequeno ? ' sm' : ''}`,
        style: `background:${CORES[morador.id % CORES.length]}`,
        'aria-hidden': 'true',
    }, morador.nome.trim().charAt(0).toUpperCase());
}

// Ícones: SVGs fixos escritos aqui (nunca vêm de dados do usuário), por isso podem entrar como HTML.
const ICONES = {
    casa: '<path d="M3 11l9-8 9 8"/><path d="M5 10v10h14V10"/>',
    recibo: '<path d="M6 3h12v18l-3-2-3 2-3-2-3 2z"/><path d="M9 8h6M9 12h6"/>',
    pessoas: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c.6-3.6 3.2-5.5 6.5-5.5s5.9 1.9 6.5 5.5"/><path d="M16 4.8a3.5 3.5 0 0 1 0 6.4M18.5 14.8c1.6.8 2.7 2.4 3 5.2"/>',
    mais: '<path d="M12 5v14M5 12h14"/>',
    cadeado: '<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
    marca: '<path d="M3 11l9-8 9 8"/><circle cx="10" cy="15" r="3.6"/><circle cx="14" cy="15" r="3.6"/>',
    copiar: '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h9"/>',
};

export function icone(nome, tamanho = 22) {
    const el = h('span', { class: 'icone', 'aria-hidden': 'true' });
    el.innerHTML = `<svg width="${tamanho}" height="${tamanho}" viewBox="0 0 24 24" fill="none" stroke="currentColor" `
        + `stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">${ICONES[nome]}</svg>`;
    return el;
}

/** A marca: telhado com dois círculos que se sobrepõem (a casa e a conta dividida). */
export function logo(comNome = true) {
    return h('div', { class: 'brand' },
        h('span', { class: 'brand-mark' }, icone('marca', 20)),
        comNome && h('span', { class: 'brand-name' }, 'república'));
}

/**
 * Roda uma ação da API mostrando o erro (se houver) num aviso.
 * Devolve true quando deu certo, para quem chamou saber se deve seguir em frente.
 */
export async function executar(acao, mensagemDeSucesso) {
    try {
        await acao();
        if (mensagemDeSucesso) toast(mensagemDeSucesso);
        return true;
    } catch (erro) {
        toast(erro.message, 'erro');
        return false;
    }
}

export const centavos = (valor) => Math.round(valor * 100);

/** Selo de situação de uma dívida em aberto (pendente, cobrada ou atrasada). */
export function badgeDivida(divida) {
    if (divida.atrasada) return h('span', { class: 'badge atrasada' }, `Atrasada desde ${dataBR(divida.vencimento, false)}`);
    if (divida.status === 'COBRADA') {
        return h('span', { class: 'badge cobrada' },
            divida.vencimento ? `Cobrada · vence ${dataBR(divida.vencimento, false)}` : 'Cobrada');
    }
    return h('span', { class: 'badge pendente' }, 'Ainda não cobrada');
}
