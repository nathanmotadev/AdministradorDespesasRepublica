import { api } from '../api.js';
import { abrirModal, confirmar, h } from '../ui.js';
import { avatar, executar } from '../components.js';

export const moradoresAtivos = (state) => state.casa.moradores.filter((m) => m.ativo);

/** "Larissa e Camilly" quando o morador tem alguém que paga junto com ele; senão, só o nome. */
export function nomeDoGrupo(state, morador) {
    const raiz = morador.responsavelId ?? morador.id;
    const grupo = moradoresAtivos(state).filter((m) => m.id === raiz || m.responsavelId === raiz)
        .sort((a, b) => (a.id === raiz ? -1 : b.id === raiz ? 1 : a.nome.localeCompare(b.nome, 'pt-BR')));
    if (grupo.length < 2) return morador.nome;
    return grupo.map((m) => m.nome).join(' e ');
}

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

    function abrirAgrupamento(morador) {
        const opcoes = moradoresAtivos(state).filter((m) => m.id !== morador.id && !m.responsavelId
            && !state.casa.moradores.some((x) => x.ativo && x.responsavelId === morador.id));
        const seletor = h('select', { 'aria-label': `Com quem ${morador.nome} paga junto` },
            h('option', { value: '' }, 'Ninguém (paga sozinho)'),
            opcoes.map((m) => h('option', { value: m.id, selected: m.id === morador.responsavelId }, m.nome)));
        const salvar = async () => {
            const id = seletor.value ? Number(seletor.value) : null;
            const ok = await executar(() => api.definirResponsavel(casaId, morador.id, id), 'Agrupamento salvo');
            if (ok) { fechar(); await recarregar(); }
        };
        const fechar = abrirModal({
            titulo: `Quem paga junto com ${morador.nome}?`,
            corpo: h('div', {},
                h('p', { class: 'dica', style: 'margin-bottom:.9rem' },
                    'A conta continua dividida por pessoa, mas a parte de quem paga junto é somada e cobrada de uma só: '
                    + 'quem escolher aqui vira o responsável. Os dois enxergam a dívida.'),
                h('div', { class: 'field' }, seletor)),
            rodape: [h('button', { class: 'btn', type: 'button', onclick: () => fechar() }, 'Cancelar'),
                h('button', { class: 'btn primary', type: 'button', onclick: salvar }, 'Salvar')],
        });
    }

    const nomeDe = (id) => state.casa.moradores.find((m) => m.id === id)?.nome;

    return h('section', { class: 'card', 'aria-labelledby': 'titulo-moradores' },
        h('div', { class: 'card-head' }, h('h2', { id: 'titulo-moradores' }, 'Moradores')),
        moradoresAtivos(state).map((morador) => h('div', { class: 'morador' },
            avatar(morador),
            h('span', { class: 'nome' }, morador.nome,
                morador.responsavelId && h('div', { class: 'dica' }, `Paga junto com ${nomeDe(morador.responsavelId)}`)),
            souAdmin && h('button', { class: 'btn small ghost', type: 'button', onclick: () => abrirAgrupamento(morador) },
                morador.responsavelId || state.casa.moradores.some((x) => x.ativo && x.responsavelId === morador.id)
                    ? 'Agrupado' : 'Agrupar'),
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
