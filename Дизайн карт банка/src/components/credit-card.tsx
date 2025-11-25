import React from 'react';
import { Wifi, CreditCard as CardIcon } from 'lucide-react';

export function CreditCard() {
  return (
    <div className="flex flex-col items-center">
      <div className="w-full max-w-[400px] h-[250px] relative group cursor-pointer">
        {/* Card Container */}
        <div className="w-full h-full bg-black rounded-2xl shadow-2xl p-6 flex flex-col justify-between relative overflow-hidden transition-transform duration-300 group-hover:scale-105">
          {/* Background Pattern */}
          <div className="absolute top-0 right-0 w-64 h-64 bg-gradient-to-br from-gray-800 to-transparent rounded-full blur-3xl opacity-50" />
          <div className="absolute bottom-0 left-0 w-48 h-48 bg-gradient-to-tr from-gray-800 to-transparent rounded-full blur-2xl opacity-50" />
          
          {/* Top Section */}
          <div className="relative z-10 flex justify-between items-start">
            <div className="flex flex-col">
              <div className="flex items-center gap-2">
                <CardIcon className="w-8 h-8 text-white" />
                <span className="text-white tracking-wider">BANK</span>
              </div>
            </div>
            <div className="flex items-center gap-3">
              {/* Premium Badge */}
              <div className="px-3 py-1 bg-white/10 backdrop-blur-sm rounded-full border border-white/20">
                <span className="text-white text-xs tracking-wider">PREMIUM</span>
              </div>
              <Wifi className="w-6 h-6 text-white rotate-90" />
            </div>
          </div>
          
          {/* Chip */}
          <div className="relative z-10">
            <div className="w-12 h-10 bg-gradient-to-br from-gray-300 via-gray-200 to-gray-100 rounded-md shadow-md">
              <div className="grid grid-cols-4 grid-rows-3 gap-[2px] p-1 h-full">
                {[...Array(12)].map((_, i) => (
                  <div key={i} className="bg-gray-400/30 rounded-sm" />
                ))}
              </div>
            </div>
          </div>
          
          {/* Card Number */}
          <div className="relative z-10">
            <div className="flex gap-3 mb-4">
              <span className="text-white tracking-[0.2em]">5412</span>
              <span className="text-white tracking-[0.2em]">7538</span>
              <span className="text-white tracking-[0.2em]">9876</span>
              <span className="text-white tracking-[0.2em]">5432</span>
            </div>
          </div>
          
          {/* Bottom Section */}
          <div className="relative z-10 flex justify-between items-end">
            <div className="flex flex-col gap-1">
              <span className="text-gray-400 text-xs tracking-wider">ВЛАДЕЛЕЦ КАРТЫ</span>
              <span className="text-white tracking-wider">IVAN PETROV</span>
            </div>
            <div className="flex gap-6 items-end">
              <div className="flex flex-col gap-1">
                <span className="text-gray-400 text-xs tracking-wider">ДЕЙСТВУЕТ</span>
                <span className="text-white tracking-wider">12/28</span>
              </div>
              {/* Mastercard Logo */}
              <svg width="40" height="24" viewBox="0 0 50 30" fill="none">
                <circle cx="15" cy="15" r="10" fill="#EB001B" opacity="0.9" />
                <circle cx="25" cy="15" r="10" fill="#F79E1B" opacity="0.9" />
              </svg>
            </div>
          </div>
        </div>
      </div>
      
      <div className="mt-6 text-center">
        <h3 className="text-black mb-2">Кредитная карта</h3>
        <p className="text-gray-600 text-sm">Premium класс с повышенным лимитом</p>
      </div>
    </div>
  );
}