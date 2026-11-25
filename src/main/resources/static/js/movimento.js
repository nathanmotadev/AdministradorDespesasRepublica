// Movimento da interface: troca de telas, entrada dos cartões, pílulas que deslizam e listas que crescem.
// A tela é recriada a cada mudança, então as animações rodam por cima do que já está no lugar certo:
// sem suporte no navegador, ou com "reduzir movimento" ligado no aparelho, tudo só aparece direto.

const CURVA = 'cubic-bezier(.22, .8, .24, 1)';
const poucoMovimento = window.matchMedia('(prefers-reduced-motion: reduce)');

export const semMovimento = () => poucoMovimento.matches;

/** Muda a tela inteira com uma transição cruzada (tema, entrar e sair). */
export function transicao(mudar) {
    if (semMovimento() || !document.startViewTransition) return mudar();
    const t = document.startViewTransition(mudar);
    t.ready.catch(() => { /* outra transição tomou o lugar: a tela muda do mesmo jeito */ });
}

// ---------- troca e entrada de telas ----------

let fantasmaAtual = null;

/**
 * Troca o conteúdo de raiz com uma transição cruzada: uma cópia da tela antiga fica por cima, parada,
 * e se desfaz enquanto a nova já entra embaixo. Não há instante de tela vazia.
 * direcao: -1 a nova vem da esquerda, 1 da direita, 0 de baixo.
 */
export function trocar(raiz, mudar, direcao = 0) {
    fantasmaAtual?.remove();
    if (semMovimento()) return mudar();

    const caixa = raiz.getBoundingClientRect();
    const fantasma = raiz.cloneNode(true);
    // o que foi digitado não vem junto na cópia
    const campos = raiz.querySelectorAll('input, textarea, select');
    fantasma.querySelectorAll('input, textarea, select').forEach((campo, i) => { campo.value = campos[i].value; });
    fantasma.querySelectorAll('[id]').forEach((el) => el.removeAttribute('id'));
    fantasma.querySelector('.fab')?.remove();
    fantasma.removeAttribute('id');
    fantasma.removeAttribute('aria-live');
    fantasma.setAttribute('aria-hidden', 'true');
    fantasma.inert = true;
    fantasma.classList.add('fantasma');
    Object.assign(fantasma.style, {
        top: `${caixa.top}px`, left: `${caixa.left}px`, width: `${caixa.width}px`,
        height: `${Math.max(0, window.innerHeight - caixa.top)}px`,
    });
    document.body.append(fantasma);
    fantasmaAtual = fantasma;

    mudar();
    entrar(raiz, direcao, 50); // a nova espera a antiga começar a sumir, para as duas não se embolarem

    const saida = fantasma.animate({ opacity: 0, transform: `translateX(${-direcao * 8}px) scale(.99)` },
        { duration: 180, easing: 'cubic-bezier(.2, .6, .4, 1)', fill: 'forwards' });
    const sumir = () => { fantasma.remove(); if (fantasmaAtual === fantasma) fantasmaAtual = null; };
    saida.finished.then(sumir, sumir);
    setTimeout(sumir, 400); // aba em segundo plano: as animações ficam paradas
    return undefined;
}

const GRUPOS = '.container, .pagina, .inicio-grade, .stack, .secao:not(.proximas), .auth';

/** Os blocos que entram um a um: abre os agrupadores e para nos cartões. */
function blocos(el) {
    if (el.matches('.fab')) return [];
    return el.matches(GRUPOS) ? [...el.children].flatMap(blocos) : [el];
}

/** Faz entrar os blocos que estão na tela. Os de baixo já ficam prontos: rolar nunca espera animação. */
export function entrar(raiz, direcao = 0, atraso = 0) {
    if (!raiz || semMovimento()) return;
    const naTela = blocos(raiz).filter((el) => {
        const caixa = el.getBoundingClientRect();
        return caixa.height > 0 && caixa.top < window.innerHeight && caixa.bottom > 0;
    });
    naTela.forEach((el, i) => {
        const tempo = { duration: 340, delay: atraso + Math.min(i, 4) * 30, easing: CURVA, fill: 'backwards' };
        const desloca = direcao ? `translateX(${direcao * 14}px)` : 'translateY(10px)';
        el.animate([{ offset: 0, opacity: 0, transform: desloca }], tempo);
        el.querySelectorAll('.progresso > span').forEach((barra) =>
            barra.animate([{ offset: 0, width: 0 }], { ...tempo, duration: 400 }));
    });
}

// ---------- listas que mudam de tamanho ----------

/** Chama mudar() (que altera o conteúdo de el) e anima a altura, para o resto da página não pular. */
export function crescer(el, mudar) {
    if (semMovimento() || !el.isConnected) return mudar();
    const antes = el.offsetHeight; // 0 se estava escondido
    mudar();
    const depois = el.offsetHeight;
    if (antes === depois) return undefined;
    const vao = antes === 0 ? parseFloat(getComputedStyle(el.parentElement).rowGap) || 0 : 0;
    el.animate([
        { height: `${antes}px`, marginBottom: `${-vao}px`, overflowY: 'clip' },
        { height: `${depois}px`, marginBottom: 0, overflowY: 'clip' },
    ], { duration: 220, easing: CURVA });
    return undefined;
}

// ---------- pílulas (abas e seletores) ----------

const PILULAS = '.nav-desktop .tab.ativo, .menu-nav .tab.ativo, .menu-tema [aria-pressed="true"], '
    + '.segmentado [aria-selected="true"], .segmentado [aria-checked="true"]';

function medirPilulas() {
    const medidas = new Map();
    document.querySelectorAll(PILULAS).forEach((el) => {
        const grupo = el.parentElement;
        const caixa = el.getBoundingClientRect();
        if (caixa.width === 0) return; // escondida neste tamanho de tela
        const origem = grupo.getBoundingClientRect();
        const chave = `${grupo.className}|${grupo.getAttribute('aria-label') ?? grupo.getAttribute('aria-labelledby')}`;
        medidas.set(chave, { el, x: caixa.left - origem.left, y: caixa.top - origem.top, l: caixa.width, a: caixa.height });
    });
    return medidas;
}

/** Redesenha e faz a marca da opção escolhida deslizar de onde estava até a nova. */
export function comPilulas(redesenhar) {
    if (semMovimento()) return redesenhar();
    const antes = medirPilulas();
    redesenhar();
    for (const [chave, novo] of medirPilulas()) {
        const velho = antes.get(chave);
        if (!velho || (Math.abs(velho.x - novo.x) < 1 && Math.abs(velho.y - novo.y) < 1)) continue;
        const tempo = { duration: 220, easing: CURVA };
        novo.el.animate([
            { transform: `translate(${velho.x - novo.x}px, ${velho.y - novo.y}px)`, width: `${velho.l}px`, height: `${velho.a}px` },
            { transform: 'none', width: `${novo.l}px`, height: `${novo.a}px` },
        ], { ...tempo, pseudoElement: '::before' });
        novo.el.animate([{ offset: 0, color: 'var(--muted)' }], tempo);
    }
    return undefined;
}

// ---------- saída ----------

/** Marca o elemento como "saindo" (o CSS anima) e o remove quando terminar. */
export function removerSuave(el, ms = 160) {
    if (semMovimento()) return el.remove();
    el.inert = true;
    el.classList.add('saindo');
    setTimeout(() => el.remove(), ms);
    return undefined;
}
