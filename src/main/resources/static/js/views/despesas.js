import { api } from '../api.js';
import { confirmar, h } from '../ui.js';
import { avatar, centavos, executar } from '../components.js';
import { brl, dataBR, deslocarMes, rotuloMes } from '../format.js';
import { abrirCobranca } from './cobranca.js';
import { abrirNovaDespesa } from './nova-despesa.js';
import { moradoresAtivos, nomeDoGrupo } from './moradores.js';

const emAberto = (despesa) => despesa.divisoes.filter((d) => !d.parteDoPagador && d.status !== 'PAGA');
const deOutros = (despesa) => despesa.divisoes.filter((d) => !d.parteDoPagador);

const MESES = ['Janeiro', 'Fevereiro', 'Março', 'Abril', 'Maio', 'Junho', 'Julho', 'Agosto', 'Setembro', 'Outubro',
    'Novembro', 'Dezembro'];

/** O vencimento mais próximo entre as partes em aberto que eu enxergo, ou null. */
function proximoVencimento(despesa) {
    const datas = despesa.divisoes.filter((d) => !d.parteDoPagador && d.status !== 'PAGA' && d.vencimento)
        .map((d) => d.vencimento).sort();
    return datas[0] ?? null;
}

/** Histórico: todas as despesas que eu enxergo, agrupadas por mês (o mais recente primeiro). */
function viewHistorico({ state }) {
    const corpo = h('div', { class: 'stack' }, h('p', { class: 'dica' }, 'Carregando…'));
    api.listarDespesas(state.casa.id).then((todas) => {
        if (todas.length === 0) {
            corpo.replaceChildren(h('div', { class: 'card vazio' }, h('strong', {}, 'Ainda não há histórico'),
                'Quando houver despesas, elas aparecem aqui, organizadas por mês.'));
            return;
        }
        const porMes = new Map();
        todas.forEach((d) => {
            const mes = d.data.slice(0, 7);
            if (!porMes.has(mes)) porMes.set(mes, []);
            porMes.get(mes).push(d);
        });
        const meses = [...porMes.keys()].sort().reverse();
        corpo.replaceChildren(...meses.map((mes, i) => {
            const lista = porMes.get(mes);
            const total = lista.reduce((soma, d) => soma + centavos(d.valorTotal), 0);
            const [ano, m] = mes.split('-');
            return h('details', { class: 'card mes-historico', open: i === 0 },
                h('summary', {},
                    h('span', { class: 'mes-nome' }, `${MESES[Number(m) - 1]} ${ano}`),
                    h('span', { class: 'dica' }, `${lista.length} ${lista.length === 1 ? 'despesa' : 'despesas'}`),
                    h('strong', { class: 'num' }, brl(total / 100))),
                h('div', { class: 'historico-lista' }, lista.map((d) => linhaHistorico(d, state))));
        }));
    }).catch((erro) => corpo.replaceChildren(h('p', { class: 'field-error' }, erro.message)));
    return corpo;
}

function linhaHistorico(despesa, state) {
    const souPagador = despesa.pagador.id === state.eu.id;
    const vence = proximoVencimento(despesa);
    const criada = despesa.criadaEm ?? despesa.data;
    return h('div', { class: 'historico-linha' },
        h('div', {},
            h('div', { class: 'nome' }, despesa.descricao),
            h('div', { class: 'dica' }, `${souPagador ? 'Você pagou' : `${despesa.pagador.nome} pagou`} · ${dataBR(despesa.data, false)}`),
            h('div', { class: 'dica' }, `Criada em ${dataBR(criada, false)}${vence ? ` · vence ${dataBR(vence, false)}` : ''}`)),
        h('div', { class: 'direita' },
            h('strong', { class: 'num' }, brl(despesa.valorTotal)),
            h('div', { class: `badge ${despesa.quitada ? 'paga' : 'pendente'}` }, despesa.quitada ? 'Quitada' : 'Em aberto')));
}

export function viewDespesas(ctx) {
    const { state, recarregar } = ctx;
    state.visaoDespesas ??= 'mes';
    const verHistorico = state.visaoDespesas === 'historico';
    const escolher = (visao) => () => { state.visaoDespesas = visao; ctx.redesenhar(); };
    const seletor = h('div', { class: 'segmentado', role: 'tablist', 'aria-label': 'Forma de ver as despesas' },
        h('button', { type: 'button', role: 'tab', 'aria-selected': String(!verHistorico), onclick: escolher('mes') }, 'Por mês'),
        h('button', { type: 'button', role: 'tab', 'aria-selected': String(verHistorico), onclick: escolher('historico') }, 'Histórico'));
    if (verHistorico) {
        return h('section', { 'aria-label': 'Histórico de despesas' }, seletor, viewHistorico(ctx));
    }
    const podeCriar = moradoresAtivos(state).length >= 2;

    async function mudarMes(delta) {
        state.mes = deslocarMes(state.mes, delta);
        await recarregar();
    }

    const cabecalho = h('div', { class: 'card-head' },
        h('div', { class: 'month-nav' },
            h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Mês anterior', onclick: () => mudarMes(-1) }, '‹'),
            h('span', { class: 'rotulo', 'aria-live': 'polite' }, rotuloMes(state.mes)),
            h('button', { class: 'icon-btn', type: 'button', 'aria-label': 'Próximo mês', onclick: () => mudarMes(1) }, '›')),
        h('button', { class: 'btn primary desktop-only', type: 'button', disabled: !podeCriar,
            onclick: () => abrirNovaDespesa(ctx) }, '+ Nova despesa'));

    return h('section', { 'aria-label': 'Despesas do mês' },
        seletor,
        cabecalho,
        !podeCriar && h('div', { class: 'card vazio' },
            h('strong', {}, 'Adicione os moradores primeiro'),
            'É preciso ter pelo menos duas pessoas na casa para dividir uma despesa.'),
        podeCriar && state.despesas.length === 0 && h('div', { class: 'card vazio' },
            h('strong', {}, 'Nenhuma despesa sua neste mês'),
            'Aqui aparecem as despesas que você pagou ou em que tem uma parte. Registre em "Nova despesa" e a divisão é automática.'),
        h('div', { class: 'stack' }, state.despesas.map((despesa) => cartaoDespesa(despesa, ctx))));
}

function cartaoDespesa(despesa, ctx) {
    const { state, recarregar } = ctx;
    const casaId = state.casa.id;
    // quem não pagou só enxerga a própria parte e não tem ações de cobrança
    const souPagador = despesa.pagador.id === state.eu.id;
    const abertas = emAberto(despesa);
    const outros = deOutros(despesa);
    const totalOutros = outros.reduce((soma, d) => soma + centavos(d.valor), 0);
    const recebido = outros.filter((d) => d.status === 'PAGA').reduce((soma, d) => soma + centavos(d.valor), 0);
    const percentual = totalOutros === 0 ? 100 : Math.round((recebido / totalOutros) * 100);
    const algumPago = outros.some((d) => d.status === 'PAGA');

    async function excluir() {
        const ok = await confirmar({
            titulo: 'Excluir despesa?',
            mensagem: `"${despesa.descricao}" e suas cobranças serão apagadas.`,
            rotulo: 'Excluir',
            perigo: true,
        });
        if (ok && await executar(() => api.excluirDespesa(casaId, despesa.id), 'Despesa excluída')) {
            await recarregar();
        }
    }

    async function confirmarRecebimento(divisao) {
        const ok = await confirmar({
            titulo: 'Confirmar recebimento',
            mensagem: `Você recebeu ${brl(divisao.valor)} de ${nomeDoGrupo(state, divisao.devedor)}?`,
            rotulo: 'Sim, recebi',
        });
        if (ok && await executar(() => api.registrarPagamento(divisao.id), 'Pagamento registrado')) {
            await recarregar();
        }
    }

    const cobrar = (divisoes) => abrirCobranca({ casaId, despesa, divisoes, recarregar });

    return h('article', { class: 'card' },
        h('div', { class: 'despesa-top' },
            h('div', {},
                h('h3', { class: 'despesa-titulo' }, despesa.descricao),
                h('p', { class: 'despesa-meta' },
                    `${souPagador ? 'Você pagou' : `${despesa.pagador.nome} pagou`} · ${dataBR(despesa.data)}`),
                despesa.criadaEm && despesa.criadaEm !== despesa.data
                    && h('p', { class: 'dica' }, `Registrada em ${dataBR(despesa.criadaEm)}`)),
            h('div', { class: 'despesa-total num' }, brl(despesa.valorTotal))),

        souPagador && h('div', { class: 'progresso', role: 'progressbar', 'aria-valuenow': percentual,
            'aria-valuemin': 0, 'aria-valuemax': 100, 'aria-label': 'Quanto já foi recebido' },
            h('span', { style: `width:${percentual}%` })),
        souPagador && h('p', { class: 'progresso-legenda num' },
            abertas.length === 0 ? 'Tudo recebido' : `${brl(recebido / 100)} de ${brl(totalOutros / 100)} recebidos`),

        h('div', { class: 'divisoes' },
            despesa.divisoes.filter((d) => !d.parteDoPagador)
                .map((d) => linhaDivisao(d, souPagador, cobrar, confirmarRecebimento, state))),

        souPagador && h('div', { class: 'card-head', style: 'margin:.75rem 0 0' },
            h('span', {}),
            h('div', { class: 'acoes' },
                abertas.length >= 2 && h('button', { class: 'btn small', type: 'button', onclick: () => cobrar(abertas) }, 'Cobrar todos'),
                h('button', { class: 'btn small', type: 'button', onclick: () => abrirNovaDespesa(ctx, despesa) }, 'Editar'),
                !algumPago && h('button', { class: 'btn small ghost', type: 'button', onclick: excluir }, 'Excluir'))));
}

function linhaDivisao(divisao, souPagador, cobrar, confirmarRecebimento, state) {
    return h('div', { class: 'divisao' },
        h('div', { class: 'quem' }, avatar(divisao.devedor, true), nomeDoGrupo(state, divisao.devedor)),
        h('span', { class: 'valor num' }, brl(divisao.valor)),
        situacao(divisao),
        souPagador && !divisao.parteDoPagador && divisao.status !== 'PAGA' && h('div', { class: 'acoes' },
            h('button', { class: 'btn small', type: 'button', onclick: () => cobrar([divisao]) },
                divisao.status === 'COBRADA' ? 'Cobrar de novo' : 'Cobrar'),
            h('button', { class: 'btn small accent', type: 'button', onclick: () => confirmarRecebimento(divisao) }, 'Recebi')));
}

function situacao(divisao) {
    if (divisao.parteDoPagador) return h('span', { class: 'badge paga' }, 'Pagou');
    if (divisao.status === 'PAGA') return h('span', { class: 'badge paga' }, `Paga em ${dataBR(divisao.pagaEm, false)}`);
    if (divisao.atrasada) return h('span', { class: 'badge atrasada' }, `Atrasada desde ${dataBR(divisao.vencimento, false)}`);
    if (divisao.status === 'COBRADA') {
        return h('span', { class: 'badge cobrada' },
            divisao.vencimento ? `Cobrada · vence ${dataBR(divisao.vencimento, false)}` : 'Cobrada');
    }
    return h('span', { class: 'badge pendente' }, 'Pendente');
}
