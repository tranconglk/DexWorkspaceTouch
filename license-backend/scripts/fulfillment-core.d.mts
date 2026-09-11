export const FULFILLMENT_STATUSES: readonly string[];
export const DEFAULT_MANIFEST_URL: string;
export class AdminClient {
  constructor(options: { apiUrl: string; token: string; fetchImpl?: typeof fetch });
  create(input: any): Promise<any>;
  show(licenseId: string): Promise<any>;
  devices(licenseId: string): Promise<any>;
  revoke(licenseId: string): Promise<any>;
  resetDevice(licenseId: string, deviceId: string): Promise<any>;
}
export class FulfillmentStore {
  constructor(directory: string);
  list(): Promise<any[]>;
  find(reference: string): Promise<any | null>;
  findByOrder(orderReference: string): Promise<any | null>;
  save(record: any): Promise<void>;
}
export class FulfillmentService {
  constructor(options: any);
  create(options: any): Promise<any>;
  markDelivered(reference: string): Promise<any>;
  revoke(reference: string, cancelled?: boolean): Promise<any>;
  resetDevice(reference: string, deviceId: string): Promise<any>;
  show(reference: string): Promise<any>;
  replace(reference: string, options: any): Promise<any>;
}
export function loadProductionManifest(fetchImpl: typeof fetch, manifestUrl: string): Promise<any>;
export function writeDeliveryArtifact(outputDirectory: string, fulfillmentId: string, release: any, licenseKey: string): Promise<string>;
