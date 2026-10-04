import { api } from '../api.js';
import { abrirModal, h, toast } from '../ui.js';
import { avatar, executar, icone } from '../components.js';
import { viewMoradores } from './moradores.js';

/** Aba "Casa": nome, convite (administrador), moradores e a conta de quem está logado. */
export function viewCasa(ctx) {
    const { state } = ctx;
    return h('div', { class: 'stack' },
        h('h1', {}, 'Casa'),
        cartaoCasa(ctx),
        state.eu.admin && cartaoConvite(ctx),
        viewMoradores(ctx),
        cartaoConta(ctx));
}

function cartaoCasa({ state, recarregar }) {
    function abrirRenomear() {
        const campo = h('input', { type: 'text', maxlength: 80, value: state.casa.nome, 'aria-label': 'Novo nome da casa' });
        const enviar = async (evento) => {
            evento.preventDefault();
            const nome = campo.value.trim();
            if (!nome) return;
            const ok = await executar(() => api.renomearCasa(state.casa.id, nome), 'Casa renomeada');
            if (ok) { fechar(); await recarregar(); }
        };
        const fechar = abrirModal({
            titulo: 'Renomear casa',
            corpo: h('form', { onsubmit: enviar }, h('div', { class: 'field' }, campo)),
            rodape: [
                h('button', { class: 'btn', type: 'button', onclick: () => fechar() }, 'Cancelar'),
                h('button', { class: 'btn primary', type: 'button', onclick: enviar }, 'Salvar'),
            ],
        });
    }

    return h('section', { class: 'card' },
        h('div', { class: 'card-head', style: 'margin:0' },
            h('div', {}, h('div', { class: 'dica' }, 'Nome da casa'), h('h2', {}, state.casa.nome)),
            state.eu.admin && h('button', { class: 'btn small', type: 'button', onclick: abrirRenomear }, 'Renomear')));
}

function cartaoConvite({ state }) {
    const codigo = h('div', { class: 'codigo num', 'aria-live': 'polite' }, '…');
    let texto = '';

    api.convite(state.casa.id)
        .then((resposta) => {
            codigo.textContent = resposta.codigo;
            texto = `Entra na "${state.casa.nome}" no república: abra ${location.origin}, toque em "Tenho um código de convite" `
                + `e use o código ${resposta.codigo}.`;
        })
        .catch((erro) => { codigo.textContent = '—'; toast(erro.message, 'erro'); });

    async function copiar() {
        if (!texto) return;
        try {
            await navigator.clipboard.writeText(texto);
            toast('Convite copiado. Agora é só mandar no WhatsApp');
        } catch {
            toast('Não consegui copiar. Selecione o código e copie manualmente', 'erro');
        }
    }

    return h('section', { class: 'card convite', 'aria-labelledby': 'titulo-convite' },
        h('h2', { id: 'titulo-convite' }, 'Convidar alguém'),
        h('p', { class: 'muted' }, 'Quem tiver este código cria a conta e entra na sua casa.'),
        codigo,
        h('button', { class: 'btn primary', type: 'button', onclick: copiar }, icone('copiar', 18), 'Copiar convite'));
}

function cartaoConta({ state, sair }) {
    return h('section', { class: 'card' },
        h('div', { class: 'morador' },
            avatar(state.eu),
            h('div', { class: 'nome' }, state.eu.nome, h('div', { class: 'dica' },
                state.eu.admin ? 'Administrador da casa' : 'Morador')),
            h('button', { class: 'btn', type: 'button', onclick: sair }, 'Sair')));
}
