import { ComponentFixture, TestBed } from '@angular/core/testing';
import QRCode from 'qrcode';
import { BookingDetailResponse } from '../../../core/models/booking.model';
import { ScheduleSearchResult, SeatResponse } from '../../../core/models/catalog.model';
import { PassengerResponse } from '../../../core/models/passenger.model';
import { NotificationService } from '../../../core/services/notification.service';
import { ETicketCardComponent } from './e-ticket-card.component';

vi.mock('jspdf', () => {
  const save = vi.fn();
  const addImage = vi.fn();
  const text = vi.fn();
  const setFontSize = vi.fn();
  return {
    jsPDF: vi.fn().mockImplementation(function (this: object) {
      return Object.assign(this, { text, setFontSize, addImage, save });
    }),
  };
});

const zipFile = vi.fn();
const zipGenerateAsync = vi.fn().mockResolvedValue(new Blob(['fake-zip']));
vi.mock('jszip', () => ({
  default: vi.fn().mockImplementation(function (this: object) {
    return Object.assign(this, { file: zipFile, generateAsync: zipGenerateAsync });
  }),
}));

describe('ETicketCardComponent', () => {
  let fixture: ComponentFixture<ETicketCardComponent>;
  let component: ETicketCardComponent;
  let notifications: NotificationService;

  const booking: BookingDetailResponse = {
    booking: {
      id: 500,
      userId: 1,
      scheduleId: 1,
      pnr: 'ABC234',
      bookingTime: '2026-08-01T00:00:00Z',
      status: 'CONFIRMED',
      totalAmount: 20,
      promoCode: null,
    },
    items: [{ id: 1, bookingId: 500, seatId: 5, passengerId: 100, fare: 20 }],
  };

  const schedule: ScheduleSearchResult = {
    scheduleId: 1,
    routeId: 1,
    type: 'BUS',
    origin: 'NYC',
    destination: 'Boston',
    venue: null,
    departureTime: '2026-08-10T00:00:00Z',
    arrivalTime: '2026-08-10T04:00:00Z',
    baseFare: 20,
    currency: 'USD',
    status: 'SCHEDULED',
    availableSeats: 2,
  };

  const seat: SeatResponse = {
    id: 5,
    scheduleId: 1,
    seatNumber: '1A',
    seatClass: 'economy',
    status: 'BOOKED',
    priceModifier: 1,
    estimatedFare: 20,
    heldUntil: null,
    heldByMe: false,
  };

  const passenger: PassengerResponse = {
    id: 100,
    userId: 1,
    fullName: 'Jane Doe',
    dob: '1990-01-01',
    idType: 'passport',
    idNumber: 'X123456',
  };

  beforeEach(async () => {
    vi.spyOn(QRCode, 'toCanvas').mockImplementation(() => Promise.resolve({} as HTMLCanvasElement));
    vi.spyOn(QRCode, 'toDataURL').mockImplementation(() => Promise.resolve('data:image/png;base64,fake'));

    await TestBed.configureTestingModule({
      imports: [ETicketCardComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ETicketCardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('booking', booking);
    fixture.componentRef.setInput('schedule', schedule);
    fixture.componentRef.setInput('seats', [seat]);
    fixture.componentRef.setInput('passengers', [passenger]);
    notifications = TestBed.inject(NotificationService);
    fixture.detectChanges();
  });

  it('renders the PNR, route, and seat/passenger row', () => {
    const html = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(html).toContain('ABC234');
    expect(html).toContain('NYC');
    expect(html).toContain('Boston');
    expect(html).toContain('1A');
    expect(html).toContain('Jane Doe');
  });

  it('labels the wallet-pass button as an unsigned demo, not a real Wallet import', () => {
    const button = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('button')).find((btn) =>
      btn.textContent?.includes('Add to Wallet'),
    );
    expect(button?.disabled).toBeFalsy();
    expect(button?.getAttribute('aria-label')).toContain('unsigned demo');
  });

  it('seatLabel and passengerName fall back to the raw id when not found', () => {
    expect(component.seatLabel(999)).toBe('#999');
    expect(component.passengerName(999)).toBe('#999');
  });

  it('emailTicket surfaces an honest "not wired up" notice instead of a fake success message', () => {
    const infoSpy = vi.spyOn(notifications, 'info');

    component.emailTicket();

    expect(infoSpy).toHaveBeenCalledWith(expect.stringContaining("isn't wired up"));
  });

  it('downloadPdf builds a PDF containing the PNR and saves it', async () => {
    await component.downloadPdf();

    const { jsPDF } = await import('jspdf');
    const instance = (jsPDF as unknown as ReturnType<typeof vi.fn>).mock.results[0].value;
    expect(instance.text).toHaveBeenCalledWith(expect.stringContaining('ABC234'), expect.any(Number), expect.any(Number));
    expect(instance.save).toHaveBeenCalledWith('ticketwave-ABC234.pdf');
  });

  describe('downloadWalletPass', () => {
    let createObjectURLSpy: ReturnType<typeof vi.spyOn>;
    let revokeObjectURLSpy: ReturnType<typeof vi.spyOn>;
    let clickSpy: ReturnType<typeof vi.spyOn>;

    beforeEach(() => {
      zipFile.mockClear();
      zipGenerateAsync.mockClear();
      createObjectURLSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:fake-url');
      revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
      clickSpy = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    });

    afterEach(() => {
      clickSpy.mockRestore();
      createObjectURLSpy.mockRestore();
      revokeObjectURLSpy.mockRestore();
    });

    it('zips an unsigned pass.json + manifest.json and triggers a .pkpass download', async () => {
      await component.downloadWalletPass();

      expect(zipFile.mock.calls[0][0]).toBe('pass.json');
      expect(ArrayBuffer.isView(zipFile.mock.calls[0][1])).toBe(true);
      expect(zipFile).toHaveBeenCalledWith('manifest.json', expect.any(String));
      expect(zipFile).not.toHaveBeenCalledWith('signature', expect.anything());
      expect(zipGenerateAsync).toHaveBeenCalledWith({ type: 'blob' });
      expect(createObjectURLSpy).toHaveBeenCalled();
      expect(clickSpy).toHaveBeenCalled();
      expect(revokeObjectURLSpy).toHaveBeenCalledWith('blob:fake-url');
    });

    it('includes the PNR as the pass serial number', async () => {
      await component.downloadWalletPass();

      const [, passBytes] = zipFile.mock.calls.find(([name]) => name === 'pass.json')!;
      const passJson = JSON.parse(new TextDecoder().decode(passBytes as Uint8Array));
      expect(passJson.serialNumber).toBe('ABC234');
      expect(passJson.boardingPass.secondaryFields).toEqual([{ key: 'seat-0', label: 'Seat', value: '1A · economy' }]);
    });

    it('manifest.json is a SHA-1 hex digest of pass.json', async () => {
      await component.downloadWalletPass();

      const [, manifestJson] = zipFile.mock.calls.find(([name]) => name === 'manifest.json')!;
      const manifest = JSON.parse(manifestJson as string);
      expect(manifest['pass.json']).toMatch(/^[0-9a-f]{40}$/);
    });

    it('surfaces an honest notice that the pass is unsigned and demo-only', async () => {
      const infoSpy = vi.spyOn(notifications, 'info');

      await component.downloadWalletPass();

      expect(infoSpy).toHaveBeenCalledWith(expect.stringContaining('unsigned demo pass'));
    });
  });
});
