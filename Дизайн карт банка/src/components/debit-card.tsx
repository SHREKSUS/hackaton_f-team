import React from 'react';
import { Wifi, CreditCard as CardIcon } from 'lucide-react';

export function DebitCard() {
  return (
    <div className="flex flex-col items-center">
      <div className="w-full max-w-[400px] h-[250px] relative group cursor-pointer">
        {/* Card Container */}
        <div className="w-full h-full bg-white rounded-2xl shadow-2xl p-6 flex flex-col justify-between relative overflow-hidden transition-transform duration-300 group-hover:scale-105">
          {/* Background Pattern */}
          <div className="absolute top-0 right-0 w-64 h-64 bg-gradient-to-br from-gray-100 to-transparent rounded-full blur-3xl opacity-50" />
          <div className="absolute bottom-0 left-0 w-48 h-48 bg-gradient-to-tr from-gray-100 to-transparent rounded-full blur-2xl opacity-50" />
          
          {/* Top Section */}
          <div className="relative z-10 flex justify-between items-start">
            <div className="flex flex-col">
              <div className="flex items-center gap-2">
                <CardIcon className="w-8 h-8 text-black" />
                <span className="text-black tracking-wider">BANK</span>
              </div>
            </div>
            <Wifi className="w-6 h-6 text-black rotate-90" />
          </div>
          
          {/* Chip */}
          <div className="relative z-10">
            <div className="w-12 h-10 bg-gradient-to-br from-yellow-200 via-yellow-300 to-yellow-400 rounded-md shadow-md">
              <div className="grid grid-cols-4 grid-rows-3 gap-[2px] p-1 h-full">
                {[...Array(12)].map((_, i) => (
                  <div key={i} className="bg-yellow-500/30 rounded-sm" />
                ))}
              </div>
            </div>
          </div>
          
          {/* Card Number */}
          <div className="relative z-10">
            <div className="flex gap-3 mb-4">
              <span className="text-black tracking-[0.2em]">4532</span>
              <span className="text-black tracking-[0.2em]">7891</span>
              <span className="text-black tracking-[0.2em]">2345</span>
              <span className="text-black tracking-[0.2em]">6789</span>
            </div>
          </div>
          
          {/* Bottom Section */}
          <div className="relative z-10 flex justify-between items-end">
            <div className="flex flex-col gap-1">
              <span className="text-gray-500 text-xs tracking-wider">ВЛАДЕЛЕЦ КАРТЫ</span>
              <span className="text-black tracking-wider">IVAN PETROV</span>
            </div>
            <div className="flex gap-6 items-end">
              <div className="flex flex-col gap-1">
                <span className="text-gray-500 text-xs tracking-wider">ДЕЙСТВУЕТ</span>
                <span className="text-black tracking-wider">12/28</span>
              </div>
              {/* Visa Logo */}
              <svg width="50" height="16" viewBox="0 0 60 20" fill="none">
                <text x="0" y="15" fill="#1A1F71" style={{ fontSize: '18px', fontWeight: 'bold', fontFamily: 'Arial' }}>VISA</text>
              </svg>
            </div>
          </div>
        </div>
      </div>
      
      <div className="mt-6 text-center">
        <h3 className="text-black mb-2">Дебетовая карта</h3>
        <p className="text-gray-600 text-sm">Для повседневных покупок и накоплений</p>
      </div>
    </div>
  );
}