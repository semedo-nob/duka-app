import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, type CapabilityKey, type SaleItem, type Settings } from './api';

// ---- Queries ----
export function useProducts() {
  return useQuery({ queryKey: ['products'], queryFn: api.products.list });
}
export function useCustomers() {
  return useQuery({ queryKey: ['customers'], queryFn: api.customers.list });
}
export function useCustomerSales(id: number | null) {
  return useQuery({
    queryKey: ['customerSales', id],
    queryFn: () => api.customers.sales(id as number),
    enabled: id !== null,
  });
}
export function useSuppliers() {
  return useQuery({ queryKey: ['suppliers'], queryFn: api.suppliers.list });
}
export function usePurchaseOrders() {
  return useQuery({ queryKey: ['purchaseOrders'], queryFn: api.purchaseOrders.list });
}
export function useBranches() {
  return useQuery({ queryKey: ['branches'], queryFn: api.branches.list });
}
export function useWarehouses() {
  return useQuery({ queryKey: ['warehouses'], queryFn: api.warehouses.list });
}
export function useExpenses() {
  return useQuery({ queryKey: ['expenses'], queryFn: api.expenses.list });
}
export function useEtims() {
  return useQuery({ queryKey: ['etims'], queryFn: api.etims.get });
}
export function useSales() {
  return useQuery({ queryKey: ['sales'], queryFn: api.sales.list });
}
export function useCapabilities() {
  return useQuery({ queryKey: ['capabilities'], queryFn: api.capabilities.get });
}
export function useTeam() {
  return useQuery({ queryKey: ['team'], queryFn: api.team.list });
}
export function useAudit() {
  return useQuery({ queryKey: ['audit'], queryFn: api.audit.list });
}
export function useSettings() {
  return useQuery({ queryKey: ['settings'], queryFn: api.settings.get });
}
export function useDashboard() {
  return useQuery({ queryKey: ['dashboard'], queryFn: api.dashboard.get });
}
export function useReports(period?: string) {
  return useQuery({ queryKey: ['reports', period], queryFn: () => api.reports.get(period) });
}
export function useProductMovements(id: number | null) {
  return useQuery({
    queryKey: ['productMovements', id],
    queryFn: () => api.products.movements(id as number),
    enabled: id !== null,
  });
}

// ---- Mutations ----
export function useAddProduct() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: api.products.create,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['products'] }),
  });
}

export function useReceiveStock() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...data }: { id: number; qty: number; cost?: number; supplier?: string }) => api.products.receive(id, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['products'] }),
  });
}

export function useAdjustStock() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...data }: { id: number; delta: number; reason?: string }) => api.products.adjust(id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['products'] });
      qc.invalidateQueries({ queryKey: ['audit'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useCreateCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: api.customers.create,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['customers'] }),
  });
}

export function useRecordPayment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, amount }: { id: number; amount: number }) => api.customers.recordPayment(id, amount),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['customers'] });
      qc.invalidateQueries({ queryKey: ['audit'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useCreateSupplier() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: api.suppliers.create,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['suppliers'] }),
  });
}

export function useCreatePurchaseOrder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: api.purchaseOrders.create,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['purchaseOrders'] });
      qc.invalidateQueries({ queryKey: ['audit'] });
    },
  });
}

export function useCreateBranch() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: api.branches.create,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['branches'] }),
  });
}

export function useTransferStock() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: api.warehouses.transfer,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['warehouses'] });
      qc.invalidateQueries({ queryKey: ['audit'] });
    },
  });
}

export function useAddExpense() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: api.expenses.create,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['expenses'] });
      qc.invalidateQueries({ queryKey: ['audit'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useRetryEtims() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: api.etims.retry,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['etims'] });
      qc.invalidateQueries({ queryKey: ['audit'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useCreateSale() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (data: { items: SaleItem[]; method: string; customerId?: number }) => api.sales.create(data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['products'] });
      qc.invalidateQueries({ queryKey: ['sales'] });
      qc.invalidateQueries({ queryKey: ['customers'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
      qc.invalidateQueries({ queryKey: ['reports'] });
      qc.invalidateQueries({ queryKey: ['etims'] });
    },
  });
}

export function useRefundSale() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, ...data }: { id: number; itemIndices: number[]; reason?: string }) => api.sales.refund(id, data),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['products'] });
      qc.invalidateQueries({ queryKey: ['sales'] });
      qc.invalidateQueries({ queryKey: ['customers'] });
      qc.invalidateQueries({ queryKey: ['audit'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
      qc.invalidateQueries({ queryKey: ['reports'] });
      qc.invalidateQueries({ queryKey: ['etims'] });
    },
  });
}

export function useUnlockCapability() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (key: CapabilityKey) => api.capabilities.unlock(key),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['capabilities'] });
      qc.invalidateQueries({ queryKey: ['audit'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useAddTeamMember() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: api.team.add,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['team'] });
      qc.invalidateQueries({ queryKey: ['audit'] });
    },
  });
}

export function useUpdateSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ section, data }: { section: keyof Settings; data: Partial<Settings[keyof Settings]> }) =>
      api.settings.update(section, data),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['settings'] }),
  });
}

