import { api } from '../api.js';
import { confirmar, h } from '../ui.js';
import { avatar, executar } from '../components.js';

export const moradoresAtivos = (state) => state.casa.moradores.filter((m) => m.ativo);

/** Quem mora na casa. Só o administrador adiciona e remove moradores. */
export function viewMoradores({ state, recarregar }) {
    const casaId = state.casa.id;
    const souAdmin = state.eu.admin;

    async function adicionar(evento) {
        evento.preventDefault();
        const campo = evento.target.elements.nome;
        const nome = campo.value.trim();
        if (!nome) return;
        const ok = await executar(() => api.adicionarMorador(casaId, nome), `${nome} entrou na casa`);
        if (ok) await recarregar();
    }

    async function remover(morador) {
        const confirmado = await confirmar({
            titulo: `Remover ${morador.nome}?`,
            mensagem: 'A pessoa sai da casa, mas as despesas antigas continuam no histórico.',
            rotulo: 'Remover',
            perigo: true,
        });
        if (!confirmado) return;
        const ok = await executar(() => api.removerMorador(casaId, morador.id), `${morador.nome} saiu da casa`);
        if (ok) await recarregar();
    }

    return h('section', { class: 'card', 'aria-labelledby': 'titulo-moradores' },
        h('div', { class: 'card-head' }, h('h2', { id: 'titulo-moradores' }, 'Moradores')),
        moradoresAtivos(state).map((morador) => h('div', { class: 'morador' },
            avatar(morador),
            h('span', { class: 'nome' }, morador.nome),
            morador.id === state.eu.id ? h('span', { class: 'dica' }, 'você')
                : morador.admin ? h('span', { class: 'badge admin' }, 'admin') : null,
            souAdmin && morador.id !== state.eu.id && h('button', { class: 'icon-btn', type: 'button',
                title: 'Remover da casa', 'aria-label': `Remover ${morador.nome}`, onclick: () => remover(morador) }, '×'))),
        souAdmin && h('form', { class: 'inline-form', onsubmit: adicionar },
            h('input', { type: 'text', name: 'nome', placeholder: 'Morador sem conta', maxlength: 80,
                autocomplete: 'off', 'aria-label': 'Nome de um morador sem conta' }),
            h('button', { class: 'btn', type: 'submit' }, 'Adicionar')),
        souAdmin && h('p', { class: 'dica', style: 'margin-top:.5rem' },
            'Quem tem celular entra pelo código de convite. Adicione aqui só quem não vai usar o app.'));
}
