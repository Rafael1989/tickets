export interface FareRuleRequest {
  routeId: number;
  seatClass: string;
  validFrom: string;
  validTo: string;
  surchargeRate: number;
}

export interface FareRuleResponse {
  id: number;
  routeId: number;
  seatClass: string;
  validFrom: string;
  validTo: string;
  surchargeRate: number;
}
