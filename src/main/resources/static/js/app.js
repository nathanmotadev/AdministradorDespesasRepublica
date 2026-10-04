// Ponto de entrada: carrega os dados da API e monta a tela.
import { api } from './api.js';
import { abrirModal, h, toast } from './ui.js';
import { mesAtual } from './format.js';
import { viewAcertos } from './views/acertos.js';
import { viewDespesas } from './views/despesas.js';
import { moradoresAtivos, viewMoradores } from './views/moradores.js';
import { abrirNovaDespesa } from './views/nova-despesa.js';
import { executar } from './components.js'

const CHAVE_CASA = 'republica.casaId';

const state = {
    casas: [],
    casaId: Number(localStorage.getItem(CHAVE_CASA)) || null,
    casa: null,
    resumo: null,
    despesas: [],
    mes: mesAtual(),
};

const ctx = { state, recarregar };
const topbar = document.getElementById('topbar');
const app = document.getElementById('app');

async function iniciar() {
    try {
        state.casas = await api.listarCasas();
        if (state.casas.length === 0) return renderizarBoasVindas();
        if (!state.casas.some((c) => c.id === state.casaId)) state.casaId = state.casas[0].id;
        await recarregar();
    } catch (erro) {
        app.replaceChildren(h('div', { class: 'card vazio full' },
            h('strong', {}, 'Não consegui falar com o servidor'), erro.message));
    }
}

/** Busca de novo tudo o que a tela mostra e redesenha. Chamado depois de qualquer alteração. */
export async function recarregar() {
    localStorage.setItem(CHAVE_CASA, String(state.casaId));
    const [casas, casa, resumo, despesas] = await Promise.all([
        api.listarCasas(),
        api.buscarCasa(state.casaId),
        api.resumo(state.casaId),
        api.listarDespesas(state.casaId, state.mes),
    ]);
    Object.assign(state, { casas, casa, resumo, despesas });
    renderizar();
}

// ---------- telas ----------

function renderizar() {

    const podeCriar = moradoresAtivos(state).length >= 2;

    topbar.replaceChildren(
        h('div', { class: 'brand' }, h('span', { class: 'brand-mark', 'aria-hidden': 'true' }, '⌂'), 'República'),
        h('select', { class: 'casa-select', 'aria-label': 'Casa', onchange: trocarCasa },
            state.casas.map((c) => h('option', { value: c.id, selected: c.id === state.casaId }, c.nome))),
        h('button', { class: 'btn small', type: 'button', onclick: abrirNovaCasa }, '+ Casa'),
        h('button', { class: 'btn small', type: 'button', onclick: abrirRenomearCasa }, 'Renomear'));

    app.replaceChildren(
        viewDespesas(ctx),
        h('aside', { class: 'sidebar' }, viewAcertos(ctx), viewMoradores(ctx)),
        h('button', { class: 'btn primary fab', type: 'button', disabled: !podeCriar,
            onclick: () => abrirNovaDespesa(ctx) }, '+ Despesa'));
}

function renderizarBoasVindas() {
    topbar.replaceChildren(h('div', { class: 'brand' },
        h('span', { class: 'brand-mark', 'aria-hidden': 'true' }, '⌂'), 'República'));

    const campo = h('input', { type: 'text', maxlength: 80, placeholder: 'Ex.: Casa da Vila', autocomplete: 'off',
        'aria-label': 'Nome da casa' });
    app.replaceChildren(h('form', { class: 'card onboarding', onsubmit: (e) => criarCasa(e, campo) },
        h('h1', {}, 'Bem-vindo à República'),
        h('p', { class: 'muted', style: 'margin-bottom:1rem' },
            'Divida contas com quem mora com você e saiba, a qualquer momento, quem deve quanto a quem. '
            + 'Comece dando um nome para a sua casa.'),
        h('div', { class: 'field' }, campo),
        h('button', { class: 'btn primary', type: 'submit' }, 'Criar casa')));
    campo.focus();
}

// ---------- ações ----------

async function trocarCasa(evento) {
    state.casaId = Number(evento.target.value);
    await recarregar();
}

async function criarCasa(evento, campo) {
    evento.preventDefault();
    const nome = campo.value.trim();
    if (!nome) return;
    try {
        const casa = await api.criarCasa(nome);
        state.casaId = casa.id;
        await recarregar();
    } catch (erro) {
        toast(erro.message, 'erro');
    }
}

function abrirNovaCasa() {
    const campo = h('input', { type: 'text', maxlength: 80, placeholder: 'Nome da casa', autocomplete: 'off',
        'aria-label': 'Nome da casa' });
    const enviar = async (evento) => {
        await criarCasa(evento, campo);
        fechar();
    };
    const corpo = h('form', { onsubmit: enviar }, h('div', { class: 'field' }, campo));
    const fechar = abrirModal({
        titulo: 'Nova casa',
        corpo,
        rodape: [
            h('button', { class: 'btn', type: 'button', onclick: () => fechar() }, 'Cancelar'),
            h('button', { class: 'btn primary', type: 'button', onclick: enviar }, 'Criar'),
        ],
    });
}
function abrirRenomearCasa() {
    const atual = state.casas.find((c) => c.id === state.casaId);
    const campo = h('input', { type: 'text', maxlength: 80, value: atual.nome, 'aria-label': 'Novo nome da casa' });
    const enviar = async (evento) => {
        evento.preventDefault();
        const nome = campo.value.trim();
        if (!nome) return;
        const ok = await executar(() => api.renomearCasa(state.casaId, nome), 'Casa renomeada');
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
iniciar();
