export interface Clock {
  nowEpochSeconds(): number;
}

export const systemClock: Clock = {
  nowEpochSeconds: () => Math.floor(Date.now() / 1000),
};
