// Ponto de entrada: decide entre a tela de entrada e o app, carrega os dados da API e monta a tela.
import { api, definirAoExpirar, sessao } from './api.js';
import { h, toast } from './ui.js';
import { mesAtual } from './format.js';
import { avatar, icone, logo } from './components.js';
import { renderAuth } from './views/auth.js';
import { viewInicio, viewLateral } from './views/inicio.js';
import { viewDespesas } from './views/despesas.js';
import { viewCasa } from './views/casa.js';
import { moradoresAtivos } from './views/moradores.js';
import { abrirNovaDespesa } from './views/nova-despesa.js';

const ABAS = [
    { id: 'inicio', rotulo: 'Início', icone: 'casa' },
    { id: 'despesas', rotulo: 'Despesas', icone: 'recibo' },
    { id: 'casa', rotulo: 'Casa', icone: 'pessoas' },
];

const estadoInicial = () => ({
    eu: null,          // o morador logado
    casa: null,        // a casa dele, com os moradores
    resumo: null,      // quanto devo / tenho a receber
    dividas: null,     // dívidas em aberto em que participo
    despesas: [],      // despesas do mês em que pago ou participo
    mes: mesAtual(),
    aba: 'inicio',
    abaDividas: null,
});

const state = estadoInicial();
const ctx = { state, recarregar, redesenhar: renderizar, irPara, sair };

const topbar = document.getElementById('topbar');
const app = document.getElementById('app');
const nav = document.getElementById('nav');

async function iniciar() {
    definirAoExpirar(mostrarEntrada);
    if (!sessao.token()) return mostrarEntrada();
    try {
        await recarregar();
    } catch (erro) {
        if (!sessao.token()) return; // a sessão expirou e a tela de entrada já foi mostrada
        app.replaceChildren(h('div', { class: 'card vazio' },
            h('strong', {}, 'Não consegui falar com o servidor'), erro.message));
    }
}

/** Busca de novo tudo o que a tela mostra e redesenha. Chamado depois de qualquer alteração. */
export async function recarregar() {
    const perfil = await api.eu();
    const [resumo, dividas, despesas] = await Promise.all([
        api.meuResumo(),
        api.minhasDividas(),
        api.listarDespesas(perfil.casa.id, state.mes),
    ]);
    Object.assign(state, { eu: perfil.morador, casa: perfil.casa, resumo, dividas, despesas });
    renderizar();
}

// ---------- telas ----------

function mostrarEntrada() {
    Object.assign(state, estadoInicial());
    renderAuth({
        topbar, app, nav,
        aoEntrar: async (resposta) => {
            sessao.salvar(resposta.token);
            try {
                await recarregar();
            } catch (erro) {
                toast(erro.message, 'erro');
            }
        },
    });
}

function renderizar() {
    topbar.replaceChildren(
        logo(),
        h('span', { class: 'casa-nome' }, state.casa.nome),
        h('span', { class: 'spacer' }),
        h('nav', { class: 'nav-desktop', 'aria-label': 'Seções' }, ABAS.map((aba) => botaoAba(aba, false))),
        h('div', { class: 'perfil' }, avatar(state.eu), h('span', { class: 'nome' }, state.eu.nome)));

    nav.replaceChildren(...ABAS.map((aba) => botaoAba(aba, true)));

    const podeCriar = moradoresAtivos(state).length >= 2;
    app.className = `container${state.aba === 'inicio' ? ' com-lateral' : ''}`;

    if (state.aba === 'inicio') {
        app.replaceChildren(viewInicio(ctx), h('aside', { class: 'lateral' }, viewLateral(ctx)));
    } else if (state.aba === 'despesas') {
        app.replaceChildren(viewDespesas(ctx));
    } else {
        app.replaceChildren(viewCasa(ctx));
    }

    if (state.aba !== 'casa') {
        app.append(h('button', { class: 'btn accent fab', type: 'button', disabled: !podeCriar,
            onclick: () => abrirNovaDespesa(ctx) }, icone('mais', 18), 'Despesa'));
    }
}

function botaoAba(aba, comIcone) {
    const ativa = state.aba === aba.id;
    return h('button', {
        class: `tab${ativa ? ' ativo' : ''}`, type: 'button',
        'aria-current': ativa ? 'page' : null,
        onclick: () => irPara(aba.id),
    }, comIcone ? icone(aba.icone) : null, aba.rotulo);
}

function irPara(aba) {
    state.aba = aba;
    renderizar();
    window.scrollTo({ top: 0 });
}

function sair() {
    sessao.sair();
    mostrarEntrada();
}

iniciar();
