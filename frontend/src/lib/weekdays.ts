export const weekdays = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"] as const;
export type Weekday = typeof weekdays[number];

export const weekdayOptions: ReadonlyArray<{ value: Weekday; label: string }> = [
  {value:"MONDAY",label:"월"},{value:"TUESDAY",label:"화"},{value:"WEDNESDAY",label:"수"},{value:"THURSDAY",label:"목"},{value:"FRIDAY",label:"금"},{value:"SATURDAY",label:"토"},{value:"SUNDAY",label:"일"},
];
