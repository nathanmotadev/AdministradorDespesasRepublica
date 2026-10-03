// Peças visuais reutilizadas em várias telas.
import { h, toast } from './ui.js';

const CORES = ['#0f766e', '#b45309', '#4f46e5', '#be185d', '#0369a1', '#6d28d9', '#15803d', '#b91c1c'];

export function avatar(morador, pequeno = false) {
    return h('span', {
        class: `avatar${pequeno ? ' sm' : ''}`,
        style: `background:${CORES[morador.id % CORES.length]}`,
        'aria-hidden': 'true',
    }, morador.nome.trim().charAt(0).toUpperCase());
}

/**
 * Roda uma ação da API mostrando o erro (se houver) num aviso.
 * Devolve true quando deu certo, para quem chamou saber se deve seguir em frente.
 */
export async function executar(acao, mensagemDeSucesso) {
    try {
        await acao();
        if (mensagemDeSucesso) toast(mensagemDeSucesso);
        return true;
    } catch (erro) {
        toast(erro.message, 'erro');
        return false;
    }
}

export const centavos = (valor) => Math.round(valor * 100);
