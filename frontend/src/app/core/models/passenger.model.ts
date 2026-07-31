export interface PassengerRequest {
  fullName: string;
  dob: string;
  idType: string;
  idNumber: string;
}

export interface PassengerResponse {
  id: number;
  userId: number;
  fullName: string;
  dob: string;
  idType: string;
  idNumber: string;
}
