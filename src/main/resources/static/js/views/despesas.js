import { api } from '../api.js';
import { confirmar, h } from '../ui.js';
import { avatar, centavos, executar } from '../components.js';
import { brl, dataBR, deslocarMes, rotuloMes } from '../format.js';
import { abrirCobranca } from './cobranca.js';
import { abrirNovaDespesa } from './nova-despesa.js';
import { moradoresAtivos } from './moradores.js';

const emAberto = (despesa) => despesa.divisoes.filter((d) => !d.parteDoPagador && d.status !== 'PAGA');
const deOutros = (despesa) => despesa.divisoes.filter((d) => !d.parteDoPagador);

export function viewDespesas(ctx) {
    const { state, recarregar } = ctx;
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
        cabecalho,
        !podeCriar && h('div', { class: 'card vazio' },
            h('strong', {}, 'Adicione os moradores primeiro'),
            'É preciso ter pelo menos duas pessoas na casa para dividir uma despesa.'),
        podeCriar && state.despesas.length === 0 && h('div', { class: 'card vazio' },
            h('strong', {}, 'Nenhuma despesa neste mês'),
            'Foi ao mercado? Pediu uma pizza? Registre em "Nova despesa" e a divisão é automática.'),
        h('div', { class: 'stack' }, state.despesas.map((despesa) => cartaoDespesa(despesa, ctx))));
}

function cartaoDespesa(despesa, { state, recarregar }) {
    const casaId = state.casa.id;
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
            mensagem: `${despesa.pagador.nome} recebeu ${brl(divisao.valor)} de ${divisao.devedor.nome}?`,
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
                h('p', { class: 'despesa-meta' }, `${despesa.pagador.nome} pagou · ${dataBR(despesa.data)}`)),
            h('div', { class: 'despesa-total num' }, brl(despesa.valorTotal))),

        h('div', { class: 'progresso', role: 'progressbar', 'aria-valuenow': percentual,
            'aria-valuemin': 0, 'aria-valuemax': 100, 'aria-label': 'Quanto já foi recebido' },
            h('span', { style: `width:${percentual}%` })),
        h('p', { class: 'progresso-legenda num' },
            abertas.length === 0 ? 'Tudo recebido' : `${brl(recebido / 100)} de ${brl(totalOutros / 100)} recebidos`),

        h('div', { class: 'divisoes' }, despesa.divisoes.map((d) => linhaDivisao(d, cobrar, confirmarRecebimento))),

        h('div', { class: 'card-head', style: 'margin:.75rem 0 0' },
            h('span', {}),
            h('div', { class: 'acoes' },
                abertas.length >= 2 && h('button', { class: 'btn small', type: 'button', onclick: () => cobrar(abertas) }, 'Cobrar todos'),
                !algumPago && h('button', { class: 'btn small ghost', type: 'button', onclick: excluir }, 'Excluir'))));
}

function linhaDivisao(divisao, cobrar, confirmarRecebimento) {
    return h('div', { class: 'divisao' },
        h('div', { class: 'quem' }, avatar(divisao.devedor, true), divisao.devedor.nome),
        h('span', { class: 'valor num' }, brl(divisao.valor)),
        situacao(divisao),
        !divisao.parteDoPagador && divisao.status !== 'PAGA' && h('div', { class: 'acoes' },
            h('button', { class: 'btn small', type: 'button', onclick: () => cobrar([divisao]) },
                divisao.status === 'COBRADA' ? 'Cobrar de novo' : 'Cobrar'),
            h('button', { class: 'btn small primary', type: 'button', onclick: () => confirmarRecebimento(divisao) }, 'Recebi')));
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
