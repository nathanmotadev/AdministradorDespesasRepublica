// Ponto de entrada: decide entre a tela de entrada e o app, carrega os dados da API e monta a tela.
import { api, definirAoExpirar, sessao } from './api.js';
import { h, toast } from './ui.js';
import { mesAtual } from './format.js';
import { avatar, icone, imagemMoradia } from './components.js';
import { alternarTema, aoMudarTema, definirTema, temaAtual } from './tema.js';
import { lembrarMoradia, mostrarAbertura } from './abertura.js';
import { atualizarAvisos, botaoAvisos } from './avisos.js';
import { comPilulas, entrar, transicao, trocar } from './movimento.js';
import { renderAuth } from './views/auth.js';
import { viewInicio } from './views/inicio.js';
import { viewDespesas } from './views/despesas.js';
import { viewResumo } from './views/resumo.js';
import { viewCasa } from './views/casa.js';
import { viewContasFixas } from './views/contas-fixas.js';
import { moradoresAtivos } from './views/moradores.js';
import { abrirNovaDespesa } from './views/nova-despesa.js';

const ABAS = [
    { id: 'inicio', rotulo: 'Início', icone: 'casa' },
    { id: 'despesas', rotulo: 'Despesas', icone: 'recibo' },
    { id: 'resumo', rotulo: 'Resumo', rotuloMenu: 'Resumo do mês', icone: 'grafico' },
    { id: 'contas', rotulo: 'Contas fixas', icone: 'repetir', soDesktop: true }, // no celular fica dentro da aba Casa
    { id: 'casa', rotulo: 'Casa', icone: 'pessoas' },
];

const ABAS_CELULAR = ABAS.filter((aba) => !aba.soDesktop);

const estadoInicial = () => ({
    eu: null,          // o morador logado
    casa: null,        // a casa dele, com os moradores
    resumo: null,      // quanto devo / tenho a receber
    dividas: null,     // dívidas em aberto em que participo
    contasFixas: null, // última lista vista: a tela já abre com ela e atualiza em seguida
    codigoConvite: null,
    despesas: [],      // despesas do mês em que pago ou participo
    mes: mesAtual(),
    aba: 'inicio',
    abaDividas: null,
});

const state = estadoInicial();
const ctx = { state, recarregar, redesenhar: renderizar, irPara, sair, reverAbertura };

const topbar = document.getElementById('topbar');
const app = document.getElementById('app');
const nav = document.getElementById('nav');
const sidebar = document.getElementById('sidebar');

async function iniciar() {
    definirAoExpirar(mostrarEntrada);
    const fecharAbertura = mostrarAbertura();
    if (!sessao.token()) {
        mostrarEntrada();
        fecharAbertura().then(() => entrar(app));
        return;
    }
    try {
        await recarregar();
    } catch (erro) {
        if (sessao.token()) { // se a sessão expirou, a tela de entrada já foi mostrada
            app.replaceChildren(h('div', { class: 'card vazio' },
                h('strong', {}, 'Não consegui falar com o servidor'), erro.message));
        }
    }
    fecharAbertura().then(() => entrar(app));
}

/** Mostra a abertura de novo (tocar no desenho da casa no card de saldo). */
function reverAbertura() {
    mostrarAbertura(state.casa?.tipo)();
}

/** Busca de novo tudo o que a tela mostra e redesenha. Chamado depois de qualquer alteração. */
export async function recarregar(opcoes) {
    await carregar();
    renderizar(opcoes);
}

async function carregar() {
    const perfil = await api.eu();
    const [resumo, dividas, despesas] = await Promise.all([
        api.meuResumo(),
        api.minhasDividas(),
        api.listarDespesas(perfil.casa.id, state.mes),
    ]);
    Object.assign(state, { eu: perfil.morador, casa: perfil.casa, resumo, dividas, despesas });
    lembrarMoradia(perfil.casa.tipo);
}

// ---------- telas ----------

function mostrarEntrada() {
    Object.assign(state, estadoInicial());
    document.body.classList.remove('com-menu');
    sidebar.replaceChildren();
    renderAuth({
        topbar, app, nav,
        aoEntrar: async (resposta) => {
            sessao.salvar(resposta.token);
            try {
                await carregar();
                transicao(() => { renderizar(); irParaTopo('instant'); });
            } catch (erro) {
                toast(erro.message, 'erro');
            }
        },
    });
}

/** Redesenha tudo. Com {entrar, direcao}, anima só a parte que mudou (o seletor CSS dela). */
function renderizar({ entrar: parte, direcao } = {}) {
    comPilulas(() => {
        renderizarMoldura();
        renderizarConteudo();
    });
    if (parte) entrar(app.querySelector(parte), direcao);
}

/** O que fica em volta do conteúdo: topo, menu lateral e barra de baixo. */
function renderizarMoldura() {
    topbar.replaceChildren(
        h('div', { class: 'ola' },
            avatar(state.eu),
            h('div', { class: 'ola-texto' },
                h('span', { class: 'ola-data' }, dataDeHoje()),
                h('span', { class: 'ola-oi' }, 'Oi, ', h('strong', {}, state.eu.nome)),
                h('span', { class: 'ola-casa' }, state.casa.nome))),
        h('span', { class: 'spacer' }),
        h('nav', { class: 'nav-desktop', 'aria-label': 'Seções' }, ABAS_CELULAR.map((aba) => botaoAba(aba, false))),
        botaoAvisos(state),
        botaoTema());
    atualizarAvisos(state);

    const podeCriar = moradoresAtivos(state).length >= 2;
    document.body.classList.add('com-menu');
    renderizarMenu();
    nav.replaceChildren(
        h('div', { class: 'nav-pilula' }, ABAS_CELULAR.map((aba) => botaoAba(aba, true))),
        h('button', { class: 'nav-despesa', type: 'button', disabled: !podeCriar,
            title: podeCriar ? null : 'Chame mais alguém para a casa antes de dividir despesas',
            onclick: () => abrirNovaDespesa(ctx) }, icone('mais', 20), 'Despesa'));

}

function renderizarConteudo() {
    if (!state.eu) return; // saiu da conta no meio de uma troca de tela
    app.className = 'container';

    if (state.aba === 'inicio') {
        app.replaceChildren(viewInicio(ctx));
    } else if (state.aba === 'despesas') {
        app.replaceChildren(viewDespesas(ctx));
    } else if (state.aba === 'resumo') {
        app.replaceChildren(viewResumo(ctx));
    } else if (state.aba === 'contas') {
        app.replaceChildren(viewContasFixas(ctx));
    } else {
        app.replaceChildren(viewCasa(ctx));
    }

    if (state.aba !== 'casa') {
        app.append(h('button', { class: 'btn accent fab', type: 'button', disabled: moradoresAtivos(state).length < 2,
            onclick: () => abrirNovaDespesa(ctx) }, icone('mais', 18), 'Despesa'));
    }
}

const dataDeHoje = () => new Date().toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' });

/** Menu lateral do computador (a partir de 1000px; no celular o CSS esconde e vale a barra de baixo). */
function renderizarMenu() {
    if (!state.eu) return;
    const moradores = moradoresAtivos(state).length;
    const podeCriar = moradores >= 2;
    sidebar.replaceChildren(
        h('div', { class: 'menu-marca' },
            imagemMoradia(state.casa.tipo, 'menu-moradia', true),
            h('span', { class: 'brand-name' }, 'República')),
        h('button', { class: 'menu-casa', type: 'button', onclick: () => irPara('casa'),
            'aria-label': `Sua casa: ${state.casa.nome}. Abrir a aba Casa` },
            h('span', { class: 'menu-casa-texto' },
                h('span', { class: 'menu-rotulo' }, 'Sua casa'),
                h('strong', {}, state.casa.nome)),
            icone('seta-baixo', 18)),
        h('nav', { class: 'menu-nav', 'aria-label': 'Seções' }, ABAS.map((aba) => botaoAba(aba, true, true))),
        h('span', { class: 'spacer' }),
        h('button', { class: 'btn accent menu-nova', type: 'button', disabled: !podeCriar,
            title: podeCriar ? null : 'Chame mais alguém para a casa antes de dividir despesas',
            onclick: () => abrirNovaDespesa(ctx) }, icone('mais', 20), 'Nova despesa'),
        h('div', { class: 'menu-tema', role: 'group', 'aria-label': 'Tema' },
            ['claro', 'escuro'].map((tema) => h('button', {
                type: 'button', 'aria-pressed': String(temaAtual() === tema),
                onclick: () => definirTema(tema),
            }, icone(tema === 'claro' ? 'sol' : 'lua', 16), tema === 'claro' ? 'Claro' : 'Escuro'))),
        h('button', { class: 'menu-perfil', type: 'button', onclick: () => irPara('casa') },
            avatar(state.eu),
            h('span', { class: 'menu-perfil-texto' },
                h('strong', {}, state.eu.nome),
                h('span', {}, 'Ver perfil')),
            icone('seta-direita', 18)));
}

/** Botão do topo que troca entre o tema claro e o escuro. */
const rotuloTema = () => (temaAtual() === 'escuro' ? 'Usar tema claro' : 'Usar tema escuro');

function botaoTema() {
    return h('button', { class: 'icon-btn redondo botao-tema', type: 'button', 'aria-label': rotuloTema(),
        title: rotuloTema(), onclick: alternarTema }, icone('sol', 20), icone('lua', 20));
}

function botaoAba(aba, comIcone, noMenu = false) {
    const ativa = state.aba === aba.id;
    return h('button', {
        class: `tab${ativa ? ' ativo' : ''}`, type: 'button',
        'aria-current': ativa ? 'page' : null,
        onclick: () => irPara(aba.id),
    }, comIcone ? icone(aba.icone) : null, noMenu ? (aba.rotuloMenu ?? aba.rotulo) : aba.rotulo);
}

const irParaTopo = (behavior) => window.scrollTo({ top: 0, behavior });

/** Troca de aba. Com destino (seletor CSS), rola até ele; tocar na aba atual volta ao topo. */
function irPara(aba, destino = null) {
    if (aba === state.aba) return irParaTopo();
    const posicao = (id) => ABAS.findIndex((a) => a.id === id);
    const direcao = Math.sign(posicao(aba) - posicao(state.aba));
    state.aba = aba;
    comPilulas(renderizarMoldura); // a aba responde na hora; o conteúdo troca em seguida
    trocar(app, () => {
        renderizarConteudo();
        irParaTopo('instant');
        if (destino) app.querySelector(destino)?.scrollIntoView({ block: 'start' });
    }, direcao);
}

function sair() {
    sessao.sair();
    transicao(() => { mostrarEntrada(); irParaTopo('instant'); });
}

aoMudarTema(() => {
    const botao = document.querySelector('.botao-tema');
    botao?.setAttribute('aria-label', rotuloTema());
    if (botao) botao.title = rotuloTema();
    // a aba Casa e o menu lateral mostram qual tema está escolhido: redesenham para acompanhar a troca
    if (state.eu && state.aba === 'casa') renderizar();
    else comPilulas(renderizarMenu);
});

iniciar();
