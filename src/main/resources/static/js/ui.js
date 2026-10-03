// Pequenos utilitários de interface: criação de elementos, modais, confirmação e avisos.
// Os textos sempre entram como nós de texto (nunca innerHTML), então não há risco de XSS.

export function h(tag, props = {}, ...children) {
    const el = document.createElement(tag);
    for (const [chave, valor] of Object.entries(props ?? {})) {
        if (valor == null || valor === false) continue;
        if (chave === 'class') el.className = valor;
        else if (chave.startsWith('on')) el.addEventListener(chave.slice(2).toLowerCase(), valor);
        else if (chave in el) el[chave] = valor;
        else el.setAttribute(chave, valor === true ? '' : valor);
    }
    for (const filho of children.flat(Infinity)) {
        if (filho == null || filho === false) continue;
        el.append(filho.nodeType ? filho : document.createTextNode(String(filho)));
    }
    return el;
}

export function toast(mensagem, tipo = 'ok') {
    const aviso = h('div', { class: `toast ${tipo}` }, mensagem);
    document.getElementById('toasts').append(aviso);
    setTimeout(() => aviso.remove(), 4200);
}

export function abrirModal({ titulo, corpo, rodape }) {
    const raiz = document.getElementById('modal-root');
    const focoAnterior = document.activeElement;

    const fechar = () => {
        sobreposicao.remove();
        document.removeEventListener('keydown', aoTeclar);
        focoAnterior?.focus?.();
    };
    const aoTeclar = (evento) => {
        if (evento.key === 'Escape') fechar();
    };

    const sobreposicao = h('div', {
            class: 'overlay',
            onclick: (e) => e.target === sobreposicao && fechar(),
        },
        h('div', { class: 'modal', role: 'dialog', 'aria-modal': 'true', 'aria-label': titulo },
            h('header', { class: 'modal-head' },
                h('h2', {}, titulo),
                h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Fechar', onclick: fechar }, '×')),
            h('div', { class: 'modal-body' }, corpo),
            rodape ? h('footer', { class: 'modal-foot' }, rodape) : null));

    document.addEventListener('keydown', aoTeclar);
    raiz.append(sobreposicao);
    sobreposicao.querySelector('input:not([type=checkbox]):not([type=radio]), select, textarea')?.focus();
    return fechar;
}

/** Pergunta "tem certeza?" e devolve uma Promise<boolean>. */
export function confirmar({ titulo, mensagem, rotulo = 'Confirmar', perigo = false }) {
    return new Promise((resolve) => {
        const fechar = abrirModal({
            titulo,
            corpo: h('p', {}, mensagem),
            rodape: [
                h('button', { class: 'btn', type: 'button', onclick: () => { fechar(); resolve(false); } }, 'Cancelar'),
                h('button', { class: `btn ${perigo ? 'danger' : 'primary'}`, type: 'button',
                    onclick: () => { fechar(); resolve(true); } }, rotulo),
            ],
        });
    });
}
