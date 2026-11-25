// Peças visuais reutilizadas em várias telas.
import { h, toast } from './ui.js';
import { dataBR } from './format.js';

const CORES = ['#e0662a', '#3f7fd8', '#2f9a62', '#c2477a', '#8a5cd6', '#c79a1e', '#1f8f9a', '#b5483a'];

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
    grafico: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
    sino: '<path d="M6 9a6 6 0 0 1 12 0c0 6 2 7 2 7H4s2-1 2-7"/><path d="M10 20a2 2 0 0 0 4 0"/>',
    'seta-baixo': '<path d="M6 9l6 6 6-6"/>',
    'seta-direita': '<path d="M9 6l6 6-6 6"/>',
    repetir: '<path d="M17 2l4 4-4 4"/><path d="M3 11V9a3 3 0 0 1 3-3h15"/><path d="M7 22l-4-4 4-4"/><path d="M21 13v2a3 3 0 0 1-3 3H3"/>',
    cadeado: '<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
    marca: '<path d="M3 11l9-8 9 8"/><circle cx="10" cy="15" r="3.6"/><circle cx="14" cy="15" r="3.6"/>',
    copiar: '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h9"/>',
    sol: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    lua: '<path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z"/>',
    raio: '<path d="M13 2L4 14h7l-1 8 9-12h-7z"/>',
    chave: '<circle cx="8" cy="15" r="4"/><path d="M11 12l9-9M17 6l3 3"/>',
    wifi: '<path d="M2 9a15 15 0 0 1 20 0M5 12.5a10 10 0 0 1 14 0M8.5 16a5 5 0 0 1 7 0"/><circle cx="12" cy="19.5" r="1"/>',
    gota: '<path d="M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11z"/>',
    chama: '<path d="M12 3c1 4 5 5.5 5 10a5 5 0 0 1-10 0c0-2.5 1.5-3.5 2-5 1 1.5 1.5 2 2 2 0-2.5 0-4.5 1-7z"/>',
    carrinho: '<circle cx="9" cy="20" r="1.4"/><circle cx="18" cy="20" r="1.4"/><path d="M2 3h3l2.7 12.4a1.5 1.5 0 0 0 1.5 1.1h8.6a1.5 1.5 0 0 0 1.5-1.1L21 7H6"/>',
};

export function icone(nome, tamanho = 22) {
    const el = h('span', { class: 'icone', 'aria-hidden': 'true' });
    el.innerHTML = `<svg width="${tamanho}" height="${tamanho}" viewBox="0 0 24 24" fill="none" stroke="currentColor" `
        + `stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">${ICONES[nome]}</svg>`;
    return el;
}

const IMAGENS = {
    CASA: { claro: 'img/casa-dia.png', escuro: 'img/casa-noite.png', alt: 'Casinha do república com uma moeda' },
    APARTAMENTO: { claro: 'img/predio-dia.png', escuro: 'img/predio-noite.png', alt: 'Prédio do república com uma moeda' },
};

/**
 * O desenho da moradia (casa ou prédio). Vão as duas versões, de dia e de noite,
 * e o CSS mostra só a do tema atual: trocar o tema não precisa redesenhar nada.
 */
export function imagemMoradia(tipo, classe = '', decorativa = false) {
    const imagem = IMAGENS[tipo] ?? IMAGENS.CASA;
    const alt = decorativa ? '' : imagem.alt;
    return h('span', { class: `moradia ${classe}`.trim() },
        h('img', { class: 'so-claro', src: imagem.claro, alt, decoding: 'async' }),
        h('img', { class: 'so-escuro', src: imagem.escuro, alt, decoding: 'async' }));
}

/** Ícone para uma conta pelo nome dela ("Luz", "Aluguel", "Internet"...). */
export function iconeDaConta(descricao) {
    const nome = descricao.toLowerCase();
    if (/luz|energia|enel|eletr/.test(nome)) return 'raio';
    if (/aluguel|condom|iptu/.test(nome)) return 'chave';
    if (/internet|wi-?fi|net\b/.test(nome)) return 'wifi';
    if (/água|agua|sabesp/.test(nome)) return 'gota';
    if (/gás|gas\b/.test(nome)) return 'chama';
    if (/mercado|compra|feira/.test(nome)) return 'carrinho';
    return 'recibo';
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
    return null;
}
