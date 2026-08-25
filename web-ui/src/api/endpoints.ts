import type { PriceBody, ProductBody } from './types'

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'

interface PathParams {
  id: string
  mode: string
}

export interface EndpointDef {
  id: string
  label: string
  method: HttpMethod
  /** Whether ?mode=normal|fail|slow (dev-only downstream fault injection) applies. */
  supportsMode: boolean
  /** Whether the path needs an id/productId placeholder filled in. */
  requiresId: boolean
  idLabel?: string
  requiresBody: boolean
  defaultBody?: ProductBody | PriceBody
  buildPath: (params: PathParams) => string
}

const defaultProductBody: ProductBody = {
  sku: 'DEMO-SKU',
  name: 'Demo Product',
  description: 'Created by the traffic simulator',
  active: true,
}

const defaultPriceBody: PriceBody = {
  productId: 1,
  amount: 19.99,
  currency: 'EUR',
}

export const endpoints: EndpointDef[] = [
  {
    id: 'products-list',
    label: 'GET /api/products',
    method: 'GET',
    supportsMode: false,
    requiresId: false,
    requiresBody: false,
    buildPath: () => '/api/products',
  },
  {
    id: 'products-get',
    label: 'GET /api/products/{id}',
    method: 'GET',
    supportsMode: true,
    requiresId: true,
    idLabel: 'Product id',
    requiresBody: false,
    buildPath: ({ id }) => `/api/products/${id}`,
  },
  {
    id: 'products-create',
    label: 'POST /api/products',
    method: 'POST',
    supportsMode: false,
    requiresId: false,
    requiresBody: true,
    defaultBody: defaultProductBody,
    buildPath: () => '/api/products',
  },
  {
    id: 'products-update',
    label: 'PUT /api/products/{id}',
    method: 'PUT',
    supportsMode: false,
    requiresId: true,
    idLabel: 'Product id',
    requiresBody: true,
    defaultBody: defaultProductBody,
    buildPath: ({ id }) => `/api/products/${id}`,
  },
  {
    id: 'products-delete',
    label: 'DELETE /api/products/{id}',
    method: 'DELETE',
    supportsMode: false,
    requiresId: true,
    idLabel: 'Product id',
    requiresBody: false,
    buildPath: ({ id }) => `/api/products/${id}`,
  },
  {
    id: 'prices-list',
    label: 'GET /api/prices',
    method: 'GET',
    supportsMode: false,
    requiresId: false,
    requiresBody: false,
    buildPath: () => '/api/prices',
  },
  {
    id: 'prices-get',
    label: 'GET /api/prices/{productId}',
    method: 'GET',
    supportsMode: true,
    requiresId: true,
    idLabel: 'Product id',
    requiresBody: false,
    buildPath: ({ id }) => `/api/prices/${id}`,
  },
  {
    id: 'prices-create',
    label: 'POST /api/prices',
    method: 'POST',
    supportsMode: false,
    requiresId: false,
    requiresBody: true,
    defaultBody: defaultPriceBody,
    buildPath: () => '/api/prices',
  },
  {
    id: 'prices-update',
    label: 'PUT /api/prices/{id}',
    method: 'PUT',
    supportsMode: false,
    requiresId: true,
    idLabel: 'Price id',
    requiresBody: true,
    defaultBody: defaultPriceBody,
    buildPath: ({ id }) => `/api/prices/${id}`,
  },
  {
    id: 'prices-delete',
    label: 'DELETE /api/prices/{id}',
    method: 'DELETE',
    supportsMode: false,
    requiresId: true,
    idLabel: 'Price id',
    requiresBody: false,
    buildPath: ({ id }) => `/api/prices/${id}`,
  },
]
